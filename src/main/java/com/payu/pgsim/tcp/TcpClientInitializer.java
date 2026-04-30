package com.payu.pgsim.tcp;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TcpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final TcpClientResponseHandler handler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new LengthFieldBasedFrameDecoder(
                        65535,
                        0,
                        2,
                        0,
                        2
                ))
                .addLast(new LengthFieldPrepender(2))
                .addLast(handler);
    }

    public TcpClientResponseHandler getHandler() {
        return handler;
    }
}

