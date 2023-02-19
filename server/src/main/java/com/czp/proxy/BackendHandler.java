package com.czp.proxy;


import io.netty.channel.*;

public class BackendHandler extends ChannelInboundHandlerAdapter {
    Channel frontChannel;

    BackendHandler(Channel ch) {
        this.frontChannel = ch;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        frontChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        System.out.println("error on:" + ctx.channel());
    }
}
