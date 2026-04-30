package com.payu.pgsim.nmm;

import com.payu.pgsim.tcp.TcpClientInitializer;
import io.netty.channel.Channel;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CLIENT-SIDE: drives the full NMM session lifecycle.
 *
 * Lifecycle:
 *   connect  →  LOGON(001)  →  [ACTIVE] → periodic ECHO(301) → LOGOFF(002) → disconnect
 *
 * Thread safety:
 *   All state mutations go through AtomicReference<NmmSessionState>.
 *   All NMM sends happen on a dedicated single-thread ScheduledExecutorService
 *   so they never block Netty I/O threads.
 *
 * Activation:
 *   Only created when pgsim.nmm.enabled=true.
 *   TcpClient (via ObjectProvider) calls onConnected() / onDisconnected().
 */
@Component
@ConditionalOnProperty(name = "pgsim.nmm.enabled", havingValue = "true")
@RequiredArgsConstructor
public class NmmClientManager {

    private static final Logger log = LoggerFactory.getLogger(NmmClientManager.class);

    private final NmmProperties        properties;
    private final NmmMessageBuilder    messageBuilder;
    private final TcpClientInitializer tcpClientInitializer;

    // ObjectProvider to break the circular TcpClient ↔ NmmClientManager dep
    private final ObjectProvider<com.payu.pgsim.tcp.TcpClient> tcpClientProvider;

    private final AtomicReference<NmmSessionState> state =
            new AtomicReference<>(NmmSessionState.DISCONNECTED);

    private volatile Channel            currentChannel;
    private volatile ScheduledFuture<?> echoFuture;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nmm-client");
                t.setDaemon(true);
                return t;
            });

    // ── Entry points called by TcpClient ────────────────────────────────────

    /**
     * Called by TcpClient immediately after a successful TCP connect.
     * Schedules LOGON in the NMM executor so the Netty connect thread is not blocked.
     */
    public void onConnected(Channel channel) {
        if (!properties.isEnabled()) return;
        this.currentChannel = channel;
        state.set(NmmSessionState.CONNECTING);
        executor.submit(this::performLogon);
    }

    /**
     * Called by TcpClient when the channel becomes inactive (disconnect / error).
     */
    public void onDisconnected() {
        if (!properties.isEnabled()) return;
        NmmSessionState prev = state.getAndSet(NmmSessionState.DISCONNECTED);
        cancelEchoSchedule();
        log.info("[CLIENT] NMM session disconnected (was {})", prev);
        if (properties.isAutoReconnect()
                && prev != NmmSessionState.LOGOFF_SENT   // intentional disconnect
                && prev != NmmSessionState.DISCONNECTED) {
            scheduleReconnect();
        }
    }

    // ── Graceful stop ────────────────────────────────────────────────────────

    /** Called by TcpClient before closing the channel. Sends LOGOFF best-effort. */
    public void prepareForShutdown() {
        if (!properties.isEnabled()) return;
        if (state.compareAndSet(NmmSessionState.ACTIVE, NmmSessionState.LOGOFF_SENT)) {
            cancelEchoSchedule();
            try {
                sendNmm("LOGOFF", messageBuilder.buildLogoff(), NmmMessageBuilder.DE70_LOGOFF);
            } catch (Exception e) {
                log.warn("[CLIENT] LOGOFF send failed (ignored on shutdown): {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        prepareForShutdown();
        executor.shutdownNow();
    }

    // ── LOGON flow ───────────────────────────────────────────────────────────

    private void performLogon() {
        if (state.get() == NmmSessionState.DISCONNECTED) return;
        state.set(NmmSessionState.LOGON_SENT);

        boolean success = false;
        int attempts = Math.max(1, properties.getRetryCount());

        for (int i = 1; i <= attempts; i++) {
            try {
                log.info("[CLIENT] Sending LOGON (attempt {}/{})", i, attempts);
                byte[] logonPayload = messageBuilder.buildLogon();
                byte[] response     = sendNmm("LOGON", logonPayload, NmmMessageBuilder.DE70_LOGON);

                if (messageBuilder.isSuccessfulNmmResponse(response)) {
                    log.info("[CLIENT] LOGON success — session ACTIVE");
                    state.set(NmmSessionState.ACTIVE);
                    startEchoSchedule();
                    success = true;
                    break;
                } else {
                    log.warn("[CLIENT] LOGON got non-00 response (attempt {})", i);
                }
            } catch (Exception e) {
                log.warn("[CLIENT] LOGON attempt {}/{} failed: {}", i, attempts, e.getMessage());
            }

            if (i < attempts) sleepQuietly(properties.getRetryDelayMs());
        }

        if (!success) {
            log.error("[CLIENT] LOGON failed after {} attempts", attempts);
            state.set(NmmSessionState.DISCONNECTED);
            if (properties.isAutoReconnect()) {
                scheduleReconnect();
            }
        }
    }

    // ── ECHO scheduler ───────────────────────────────────────────────────────

    private void startEchoSchedule() {
        cancelEchoSchedule();
        echoFuture = executor.scheduleAtFixedRate(
                this::performEcho,
                properties.getEchoIntervalMs(),
                properties.getEchoIntervalMs(),
                TimeUnit.MILLISECONDS);
        log.info("[CLIENT] ECHO scheduler started (every {} ms)", properties.getEchoIntervalMs());
    }

    private void cancelEchoSchedule() {
        ScheduledFuture<?> f = echoFuture;
        if (f != null && !f.isCancelled()) {
            f.cancel(false);
        }
        echoFuture = null;
    }

    private void performEcho() {
        if (state.get() != NmmSessionState.ACTIVE) return;

        int attempts = Math.max(1, properties.getRetryCount());
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= attempts; i++) {
            try {
                log.info("[CLIENT] Sending ECHO (attempt {}/{})", i, attempts);
                byte[] echoPayload = messageBuilder.buildEcho();
                byte[] response    = sendNmm("ECHO", echoPayload, NmmMessageBuilder.DE70_ECHO);

                if (messageBuilder.isSuccessfulNmmResponse(response)) {
                    log.info("[CLIENT] ECHO success");
                    return;
                } else {
                    log.warn("[CLIENT] ECHO got non-00 response (attempt {})", i);
                    failCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.warn("[CLIENT] ECHO attempt {}/{} failed: {}", i, attempts, e.getMessage());
                failCount.incrementAndGet();
            }

            if (i < attempts) sleepQuietly(properties.getRetryDelayMs());
        }

        log.error("[CLIENT] ECHO failed after {} attempts — triggering reconnect", attempts);
        state.set(NmmSessionState.DISCONNECTED);
        cancelEchoSchedule();
        triggerReconnect();
    }

    // ── Reconnect logic ──────────────────────────────────────────────────────

    private void scheduleReconnect() {
        executor.schedule(this::triggerReconnect, properties.getRetryDelayMs(), TimeUnit.MILLISECONDS);
    }

    private void triggerReconnect() {
        log.info("[CLIENT] Initiating auto-reconnect...");
        try {
            com.payu.pgsim.tcp.TcpClient tcpClient = tcpClientProvider.getIfAvailable();
            if (tcpClient == null) {
                log.warn("[CLIENT] TcpClient not available for reconnect");
                return;
            }
            tcpClient.stop();
            Thread.sleep(500);
            tcpClient.start();
            // onConnected() will be called again by TcpClient after a successful connect
        } catch (Exception e) {
            log.error("[CLIENT] Reconnect attempt failed: {}", e.getMessage());
        }
    }

    // ── NMM send helper ──────────────────────────────────────────────────────

    /**
     * Sends a packed NMM message and waits for the response.
     * Correlates via DE11 (STAN).
     */
    private byte[] sendNmm(String label, byte[] payload, String de70) throws Exception {
        Channel ch = currentChannel;
        if (ch == null || !ch.isActive()) {
            throw new IllegalStateException("No active channel for NMM " + label);
        }
        String stan = messageBuilder.extractStan(payload);
        String correlationKey = stan != null ? "11:" + stan : null;

        return tcpClientInitializer.getHandler()
                .sendAndAwait(ch, payload, correlationKey, properties.getResponseTimeoutMs());
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Externally readable state (e.g. for health/status endpoints). */
    public NmmSessionState getSessionState() {
        return state.get();
    }
}
