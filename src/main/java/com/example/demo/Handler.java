package com.example.demo;


import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;

public class Handler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String outString = "hello good boy";
        var outmsg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        outmsg.headers().set("abc", "123");
        outmsg.headers().set("content-length", outString.length());
        outmsg.content().writeBytes(outString.getBytes());
        var future = ctx.writeAndFlush(outmsg);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                future.channel().close();
            }
        });

    }
}
