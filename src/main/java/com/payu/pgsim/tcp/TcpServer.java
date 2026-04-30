package com.payu.pgsim.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulator.tcp.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TcpServer {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final TcpServerInitializer tcpServerInitializer;

    @Value("${simulator.tcp.port}")
    private int port;

    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;
    private volatile Thread serverThread;

    public synchronized void start() {
        if (isRunning()) {
            return;
        }

        this.serverThread = new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            try {

                ServerBootstrap bootstrap = new ServerBootstrap();

                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(tcpServerInitializer)
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannel = future.channel();

                log.info("ISO8583 TCP Server started on port {}", port);

                serverChannel.closeFuture().sync();

            } catch (Exception e) {

                log.error("TCP server failed", e);

            } finally {
                serverChannel = null;
                if (workerGroup != null) {
                    workerGroup.shutdownGracefully();
                }
                if (bossGroup != null) {
                    bossGroup.shutdownGracefully();
                }
                workerGroup = null;
                bossGroup = null;
                serverThread = null;
            }
        }, "pgsim-tcp-server");
        this.serverThread.setDaemon(true);
        this.serverThread.start();
    }

    public synchronized void stop() {
        Channel c = serverChannel;
        if (c != null) {
            c.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        serverChannel = null;
        workerGroup = null;
        bossGroup = null;
    }

    public boolean isRunning() {
        Channel c = serverChannel;
        return c != null && c.isActive();
    }

    @PreDestroy
    public void onDestroy() {
        try {
            stop();
        } catch (Exception e) {
            log.debug("Ignoring TCP server shutdown exception: {}", e.getMessage());
        }
    }
}