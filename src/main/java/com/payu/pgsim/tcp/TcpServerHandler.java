// src/main/java/com/payu/pgsim/tcp/TcpServerHandler.java
package com.payu.pgsim.tcp;

import com.payu.pgsim.handler.MessageHandler;
import com.payu.pgsim.handler.ProcessingResult;
import com.payu.pgsim.model.ConnectionInfo;
import com.payu.pgsim.nmm.NmmServerObserver;
import com.payu.pgsim.store.ConnectionStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Component
@ChannelHandler.Sharable
public class TcpServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpServerHandler.class);
    private static final AttributeKey<Boolean> ACQUIRED = AttributeKey.valueOf("pgsimConnAcquired");

    private final MessageHandler messageHandler;
    private final ExecutorService isoProcessingExecutor;
    private final ConnectionStore connectionStore;
    private final TcpConnectionGate connectionGate;

    // ObjectProvider — NmmServerObserver only exists when pgsim.nmm.enabled is active
    private final ObjectProvider<NmmServerObserver> nmmObserverProvider;

    // Netty channel -> connectionId
    private final ConcurrentHashMap<ChannelId, String> connectionState = new ConcurrentHashMap<>();

    public TcpServerHandler(
            MessageHandler messageHandler,
            ExecutorService isoProcessingExecutor,
            ConnectionStore connectionStore,
            TcpConnectionGate connectionGate,
            ObjectProvider<NmmServerObserver> nmmObserverProvider) {
        this.messageHandler = messageHandler;
        this.isoProcessingExecutor = isoProcessingExecutor;
        this.connectionStore = connectionStore;
        this.connectionGate = connectionGate;
        this.nmmObserverProvider = nmmObserverProvider;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!connectionGate.tryAcquire()) {
            log.warn("Rejecting connection (max connections): {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        ctx.channel().attr(ACQUIRED).set(Boolean.TRUE);

        String connectionId = UUID.randomUUID().toString();
        connectionState.put(ctx.channel().id(), connectionId);

        ConnectionInfo info = new ConnectionInfo();
        info.setConnectionId(connectionId);
        info.setConnectedAt(LocalDateTime.now());
        info.setLastActivity(LocalDateTime.now());
        info.setMessageCount(0);
        info.setStatus("ACTIVE");

        SocketAddress remote = ctx.channel().remoteAddress();
        if (remote instanceof InetSocketAddress inet) {
            info.setRemoteAddress(inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString());
            info.setRemotePort(inet.getPort());
        } else if (remote != null) {
            info.setRemoteAddress(remote.toString());
            info.setRemotePort(-1);
        } else {
            info.setRemoteAddress("UNKNOWN");
            info.setRemotePort(-1);
        }

        SocketAddress local = ctx.channel().localAddress();
        if (local instanceof InetSocketAddress lin) {
            info.setLocalPort(lin.getPort());
        } else {
            info.setLocalPort(-1);
        }

        connectionStore.add(info);

        log.info("------------------------------------------------------------------------------");
        log.info("Client connected: {} | ConnectionId={}", remote, connectionId);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (Boolean.TRUE.equals(ctx.channel().attr(ACQUIRED).getAndSet(false))) {
            connectionGate.release();
        }

        String connectionId = connectionState.remove(ctx.channel().id());

        if (connectionId != null) {
            ConnectionInfo info = connectionStore.get(connectionId);
            if (info != null) {
                info.setLastActivity(LocalDateTime.now());
                info.setStatus("CLOSED");
                // choose one behavior:
                // keep for monitoring history:
                connectionStore.add(info);
                // or remove to show only active:
                // connectionStore.remove(connectionId);
            }
        }

        // Clean up NMM session on disconnect
        NmmServerObserver nmmObserver = nmmObserverProvider.getIfAvailable();
        if (nmmObserver != null && connectionId != null) {
            nmmObserver.onDisconnect(connectionId);
        }

        log.info("Client disconnected: {}", connectionId);
        log.info("------------------------------------------------------------------------------");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buffer = (ByteBuf) msg;

        byte[] request = new byte[buffer.readableBytes()];
        buffer.readBytes(request);
        ReferenceCountUtil.release(buffer);

        String connectionId = connectionState.get(ctx.channel().id());
        log.info("Received ISO8583 message | ConnectionId={}", connectionId);

        if (connectionId != null) {
            ConnectionInfo info = connectionStore.get(connectionId);
            if (info != null) {
                info.setLastActivity(LocalDateTime.now());
                info.setMessageCount(info.getMessageCount() + 1);
            }
        }

        // NMM observation — additive, never affects the response path
        NmmServerObserver nmmObserver = nmmObserverProvider.getIfAvailable();
        if (nmmObserver != null) {
            nmmObserver.observe(connectionId, request);
        }

        isoProcessingExecutor.submit(() -> {
            ProcessingResult result = messageHandler.processTcp(request, connectionId);

            ctx.channel().eventLoop().execute(() -> {
                if (!ctx.channel().isActive()) {
                    return;
                }

                if (result.closeChannel()) {
                    ctx.close();
                    return;
                }

                byte[] response = result.response();
                if (response != null && response.length > 0) {
                    ctx.writeAndFlush(Unpooled.copiedBuffer(response));
                }
            });
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            String connectionId = connectionState.get(ctx.channel().id());
            log.info("Connection timeout: {}", connectionId);

            if (connectionId != null) {
                ConnectionInfo info = connectionStore.get(connectionId);
                if (info != null) {
                    info.setLastActivity(LocalDateTime.now());
                    info.setStatus("TIMEOUT");
                }
            }

            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Connection error: {}", cause.getMessage(), cause);

        String connectionId = connectionState.get(ctx.channel().id());
        if (connectionId != null) {
            ConnectionInfo info = connectionStore.get(connectionId);
            if (info != null) {
                info.setLastActivity(LocalDateTime.now());
                info.setStatus("ERROR");
            }
        }

        ctx.close();
    }
}