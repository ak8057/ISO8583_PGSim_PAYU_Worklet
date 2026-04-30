package com.payu.pgsim.tcp;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TcpServerInitializer extends ChannelInitializer<SocketChannel> {

    private final TcpServerHandler tcpServerHandler;
    private final TlsSupport tlsSupport;

    @Value("${pgsim.connection.timeout}")
    private int timeout;

    @Override
    protected void initChannel(SocketChannel ch) {

        SslContext ssl = tlsSupport.sslContext();
        if (ssl != null) {
            ch.pipeline().addLast(ssl.newHandler(ch.alloc()));
        }

        ch.pipeline()

                // 30 second idle timeout
                .addLast(new IdleStateHandler(timeout/1000, 0, 0))

                // ISO8583 frame decoder
                .addLast(new LengthFieldBasedFrameDecoder(
                        65535,
                        0,
                        2,
                        0,
                        2
                ))

                .addLast(new LengthFieldPrepender(2))

                // main handler
                .addLast(tcpServerHandler);
    }
}