package com.example.demo;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;

public class FrontHandler extends ChannelInboundHandlerAdapter {

    Channel backendChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            var req = (HttpRequest) msg;
            if (((HttpRequest) msg).method().equals("CONNECT") == false) {
                ctx.fireExceptionCaught(new Exception("http method is not CONNECT"));
            }
            String[] hostport = req.headers().get("Host").split(":");
            String host = hostport[0];
            Integer port = Integer.valueOf(hostport.length == 1 ? "80" : hostport[1]);
            createOutChannel(ctx, host, port);
        } else {
            backendChannel.writeAndFlush(msg);
        }
    }

    private void createOutChannel(ChannelHandlerContext ctx, String host, Integer port) {
        Bootstrap strap = new Bootstrap();
        strap.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new LoggingHandler(LogLevel.INFO))
                                .addLast(new BackendHandler(ctx.channel()));
                    }
                });
        ChannelFuture channelFuture = strap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener((ChannelFutureListener) future1 -> {
            HttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            ctx.writeAndFlush(resp).addListener((ChannelFutureListener) future2 -> ctx.pipeline().remove("http"));
        });
        backendChannel = channelFuture.channel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}
