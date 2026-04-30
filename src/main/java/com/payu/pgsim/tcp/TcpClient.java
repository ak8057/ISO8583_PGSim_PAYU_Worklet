package com.payu.pgsim.tcp;

import com.payu.pgsim.nmm.NmmClientManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty ISO8583 TCP client runtime used when simulator.mode=CLIENT.
 * Uses same 2-byte length framing as server pipeline.
 */
@Component
@ConditionalOnProperty(name = "simulator.tcp.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TcpClient {

    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    private final TcpClientInitializer tcpClientInitializer;

    // ObjectProvider breaks the NmmClientManager ↔ TcpClient circular dependency
    private final ObjectProvider<NmmClientManager> nmmClientManagerProvider;

    @Value("${simulator.client.host:127.0.0.1}")
    private String host;

    @Value("${simulator.client.port:8080}")
    private int port;

    @Value("${simulator.client.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${simulator.client.response-timeout-ms:10000}")
    private long responseTimeoutMs;

    @Value("${simulator.client.retry-count:2}")
    private int retryCount;

    @Value("${simulator.client.retry-delay-ms:500}")
    private long retryDelayMs;

    private EventLoopGroup group;
    private Bootstrap bootstrap;
    private volatile Channel channel;

    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        this.group = new NioEventLoopGroup(1);
        this.bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .handler(tcpClientInitializer);

        log.info("ISO8583 CLIENT mode selected. Target {}:{}", host, port);
        connect();
    }

    @PreDestroy
    public synchronized void stop() {
        // Notify NMM manager so it can send LOGOFF before the channel closes
        NmmClientManager nmm = nmmClientManagerProvider.getIfAvailable();
        if (nmm != null) {
            nmm.prepareForShutdown();
        }
        try {
            Channel c = this.channel;
            if (c != null) {
                c.close().syncUninterruptibly();
            }
        } catch (Exception ignored) {
            // no-op
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        channel = null;
        bootstrap = null;
        group = null;
    }

    public byte[] sendAndReceive(byte[] payload) throws Exception {
        return sendAndReceive(payload, null);
    }

    public byte[] sendAndReceive(byte[] payload, String correlationKey) throws Exception {
        Exception last = null;
        int attempts = Math.max(0, retryCount) + 1;
        for (int i = 1; i <= attempts; i++) {
            try {
                ensureConnected();
                log.info("[CLIENT] Sending ISO message to server...");
                return tcpClientInitializer.getHandler()
                        .sendAndAwait(channel, payload, correlationKey, responseTimeoutMs);
            } catch (Exception e) {
                last = e;
                log.warn("TcpClient attempt {}/{} failed: {}", i, attempts, e.getMessage());
                closeChannel();
                if (i < attempts && retryDelayMs > 0) {
                    Thread.sleep(retryDelayMs);
                }
            }
        }
        throw (last != null) ? last : new IllegalStateException("TCP send failed");
    }

    private synchronized void ensureConnected() throws Exception {
        if (channel != null && channel.isActive()) {
            return;
        }
        connect();
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("TCP client not connected to " + host + ":" + port);
        }
    }

    private synchronized void connect() {
        try {
            ChannelFuture future = bootstrap.connect(host, port).sync();
            this.channel = future.channel();
            log.info("TcpClient connected to {}:{}", host, port);
            // Trigger NMM LOGON lifecycle on a separate thread to not block the connect
            NmmClientManager nmm = nmmClientManagerProvider.getIfAvailable();
            if (nmm != null) {
                nmm.onConnected(this.channel);
            }
        } catch (Exception e) {
            this.channel = null;
            log.warn("TcpClient initial connection failed: {}", e.getMessage());
        }
    }

    private synchronized void closeChannel() {
        try {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
        } finally {
            channel = null;
        }
    }

    public boolean isRunning() {
        Channel c = channel;
        return c != null && c.isActive();
    }
}
