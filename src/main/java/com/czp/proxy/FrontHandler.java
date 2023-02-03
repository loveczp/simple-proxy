package com.czp.proxy;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;

public class FrontHandler extends ChannelInboundHandlerAdapter {
    Channel backendChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            String method = req.method().name();
            if (method.equals("CONNECT") == true) {
                String[] hostport = req.headers().get("Host").split(":");
                String host = hostport[0];
                Integer port = Integer.valueOf(hostport.length == 1 ? "80" : hostport[1]);
                createOutChannel(ctx, host, port);
            } else if (method.equals("GET") == true && req.uri().startsWith("/health")) {
                handleHealthRequest(ctx, req);
            } else {
                ctx.fireExceptionCaught(new RuntimeException("wrong http request:" + msg));
                ctx.close();
            }
        } else if (this.backendChannel != null) {
            backendChannel.writeAndFlush(msg);
        } else {
            ctx.fireExceptionCaught(new RuntimeException("wrong raw request:" + msg));
            ctx.close();
        }
    }

    private void createOutChannel(ChannelHandlerContext ctx, String host, Integer port) {
        Bootstrap strap = new Bootstrap();
        strap.group(ctx.channel().eventLoop());
        strap.channel(ctx.channel().getClass());
        strap.option(ChannelOption.TCP_NODELAY, true);
        strap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline()
                        .addLast("back", new BackendHandler(ctx.channel()));
            }
        });
        ChannelFuture channelFuture = strap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener(f1 -> {
            var resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            ctx.writeAndFlush(resp).addListener(f2 -> ctx.pipeline().remove("http"));
        });
        backendChannel = channelFuture.channel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }


    void handleHealthRequest(ChannelHandlerContext ctx, HttpRequest req) {
        var content = "this is dummy data, for test only.";
        var resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length());
        resp.content().writeBytes("this is dummy data, for test only.".getBytes());
        ctx.writeAndFlush(resp).addListener(x -> ctx.close());
    }

}
