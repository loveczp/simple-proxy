package com.example.demo;


import io.netty.channel.*;
import io.netty.handler.codec.http.*;


public class BackendHandler extends SimpleChannelInboundHandler<HttpObject> {

    Channel frontChannel;

    BackendHandler(Channel ch) {
        this.frontChannel = ch;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        ctx.writeAndFlush(request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        frontChannel.writeAndFlush(msg);
    }
}
