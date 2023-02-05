package com.czp.proxy.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public class ExampleToGoogle extends SimpleChannelInboundHandler<HttpObject> {

    public static void main(String[] arg) throws InterruptedException {
        var b = new Bootstrap();
        b.group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new LoggingHandler(LogLevel.INFO))
                                .addLast(new HttpClientCodec())
                                .addLast(new ExampleToGoogle());
                    }
                });
        b.connect(new InetSocketAddress("www.google.com", 80)).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                future.channel().writeAndFlush(request);
            }
        });


    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof DefaultHttpContent) {
            DefaultHttpContent content = (DefaultHttpContent) msg;
            System.out.print("msg: " + content.content().toString(Charset.defaultCharset()));
        } else if (msg instanceof LastHttpContent) {
            System.err.print("\nEND OF CONTENT");
            ctx.close();
            ctx.channel().eventLoop().shutdownGracefully();
        } else {
            System.out.print("msg: " + msg.toString());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
        ctx.channel().eventLoop().shutdownGracefully();
    }
}
