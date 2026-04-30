package com.payu.pgsim.tcp;

import com.payu.pgsim.nmm.NmmClientManager;
import com.payu.pgsim.nmm.NmmMessageBuilder;
import com.payu.pgsim.parser.Iso8583Parser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ChannelHandler.Sharable
public class TcpClientResponseHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpClientResponseHandler.class);
    private static final String DEFAULT_KEY = "__DEFAULT__";

    private final Iso8583Parser parser;

    // ObjectProvider to avoid circular dep: NmmClientManager → TcpClientInitializer → handler
    private final ObjectProvider<NmmClientManager> nmmClientManagerProvider;

    private final Map<String, CompletableFuture<byte[]>> pendingByKey = new ConcurrentHashMap<>();

    public TcpClientResponseHandler(Iso8583Parser parser,
                                    ObjectProvider<NmmClientManager> nmmClientManagerProvider) {
        this.parser = parser;
        this.nmmClientManagerProvider = nmmClientManagerProvider;
    }

    public byte[] sendAndAwait(Channel channel, byte[] payload, String correlationKey, long timeoutMs) throws Exception {
        String key = normalizeKey(correlationKey);
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = pendingByKey.putIfAbsent(key, future);
        if (existing != null && !existing.isDone()) {
            throw new IllegalStateException("Another request is already in-flight for key " + key);
        }
        future.whenComplete((r, t) -> pendingByKey.remove(key, future));
        if (timeoutMs > 0) {
            future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        }
        ChannelFuture writeFuture = channel.writeAndFlush(channel.alloc().buffer(payload.length).writeBytes(payload));
        writeFuture.addListener(f -> {
            if (!f.isSuccess()) {
                CompletableFuture<byte[]> p = pendingByKey.get(key);
                if (p != null && !p.isDone()) {
                    p.completeExceptionally(
                            f.cause() != null ? f.cause() : new IllegalStateException("TCP write failed"));
                }
            }
        });

        try {
            return future.get(timeoutMs > 0 ? timeoutMs + 200 : 10000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            CompletableFuture<byte[]> p = pendingByKey.get(key);
            if (p != null && !p.isDone()) {
                p.completeExceptionally(e);
            }
            throw e;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            ByteBuf buffer = (ByteBuf) msg;
            byte[] response = new byte[buffer.readableBytes()];
            buffer.readBytes(response);

            // Log NMM response type before routing to pending futures
            logNmmResponseIfApplicable(response);

            String key = extractCorrelationKey(response);
            CompletableFuture<byte[]> f = pendingByKey.get(key);
            if (f != null && !f.isDone()) {
                f.complete(response);
            } else if (pendingByKey.size() == 1) {
                // Best effort fallback when correlation fields are missing.
                CompletableFuture<byte[]> only = pendingByKey.values().iterator().next();
                if (!only.isDone()) {
                    only.complete(response);
                }
            } else {
                log.warn("Received unexpected client response with no matching key {}", key);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void logNmmResponseIfApplicable(byte[] response) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(parser.getPackager());
            msg.unpack(response);
            if (!"0810".equals(msg.getMTI())) return;

            String de70 = msg.hasField(70) ? msg.getString(70) : null;
            String de39 = msg.hasField(39) ? msg.getString(39) : "?";
            switch (de70 != null ? de70 : "") {
                case NmmMessageBuilder.DE70_LOGON  -> log.info("[CLIENT] LOGON  response received  DE39={}", de39);
                case NmmMessageBuilder.DE70_LOGOFF -> log.info("[CLIENT] LOGOFF response received  DE39={}", de39);
                case NmmMessageBuilder.DE70_ECHO   -> log.info("[CLIENT] ECHO   response received  DE39={}", de39);
                default -> log.debug("[CLIENT] 0810 with DE70={} DE39={}", de70, de39);
            }
        } catch (Exception ignored) {
            // never break the read flow
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        for (CompletableFuture<byte[]> f : pendingByKey.values()) {
            if (!f.isDone()) {
                f.completeExceptionally(cause);
            }
        }
        pendingByKey.clear();
        log.warn("TcpClientResponseHandler error: {}", cause.getMessage(), cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        IllegalStateException closed = new IllegalStateException("TCP client channel became inactive");
        for (CompletableFuture<byte[]> f : pendingByKey.values()) {
            if (!f.isDone()) {
                f.completeExceptionally(closed);
            }
        }
        pendingByKey.clear();
        // Notify NMM manager so it can trigger auto-reconnect if configured
        NmmClientManager nmm = nmmClientManagerProvider.getIfAvailable();
        if (nmm != null) {
            nmm.onDisconnected();
        }
    }

    private String extractCorrelationKey(byte[] response) {
        try {
            ISOMsg msg = parser.parse(response);
            if (msg.hasField(11)) {
                return "11:" + msg.getString(11);
            }
            if (msg.hasField(37)) {
                return "37:" + msg.getString(37);
            }
            return DEFAULT_KEY;
        } catch (Exception e) {
            return DEFAULT_KEY;
        }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_KEY;
        }
        return key.trim();
    }
}

