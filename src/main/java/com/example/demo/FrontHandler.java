package com.example.demo;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;

public class FrontHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        if (msg instanceof HttpRequest) {
            var req = (HttpRequest) msg;
            createOutChannel(ctx, req.headers().get(HttpHeaderNames.HOST),80);
        }else{
            System.out.println("none http request: "+msg.getClass());
        }
    }

    private void createOutChannel(ChannelHandlerContext ctx, String host, Integer port) throws InterruptedException {
        Bootstrap strap = new Bootstrap();
        strap.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() { // (4)
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new LoggingHandler(LogLevel.INFO))
                                .addLast(new HttpClientCodec())
                                .addLast(new BackendHandler(ctx.channel()));
                    }
                });
        strap.connect(new InetSocketAddress("www.google.com", port));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
}
