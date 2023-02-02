package com.czp.proxy;

import io.netty.bootstrap.ServerBootstrap;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

public class Application {

    public static void main(String[] args) throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        var cert = Application.class.getClassLoader().getResourceAsStream("cert.pem");
        var key = Application.class.getClassLoader().getResourceAsStream("key.pem");
        try {
            var sslContext = SslContextBuilder.forServer(cert, key)
                    .build();
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws CertificateException, SSLException {
                            ch.pipeline()
//                                    .addLast("log", new LoggingHandler(LogLevel.INFO))
                                    .addLast("ssl", sslContext.newHandler(ch.alloc()))
                                    .addLast("http", new HttpServerCodec())
                                    .addLast("front", new FrontHandler());
                        }
                    }).option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);


            ChannelFuture f = b.bind(8080).sync();
            System.out.println("server is up on port: " + 8080);
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

}
