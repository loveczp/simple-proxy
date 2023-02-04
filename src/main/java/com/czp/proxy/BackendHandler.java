package com.czp.proxy;


import io.netty.channel.*;
import io.netty.handler.codec.http.LastHttpContent;


public class BackendHandler extends ChannelInboundHandlerAdapter {
    Channel frontChannel;

    BackendHandler(Channel ch) {
        this.frontChannel = ch;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof LastHttpContent) {
            System.err.print("\nEND OF CONTENT");
            ctx.close();
            ctx.channel().eventLoop().shutdownGracefully();
        } else {
            frontChannel.writeAndFlush(msg);
        }
    }
}
