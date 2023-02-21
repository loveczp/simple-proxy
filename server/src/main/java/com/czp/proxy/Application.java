package com.czp.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.util.List;
import javax.net.ssl.SSLException;

public class Application {

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        var cert = Application.class.getClassLoader().getResourceAsStream("cert.pem");
        var key = Application.class.getClassLoader().getResourceAsStream("key.pem");
        try {
            SslContext clientSslContext = SslContextBuilder.forClient().build();
            SslContext serverSslContext = SslContextBuilder.forServer(cert, key).build();
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws CertificateException, SSLException {
                            ch.pipeline()
                                    .addLast("ssl", serverSslContext.newHandler(ch.alloc()))
                                    // .addLast("log", new LoggingHandler(LogLevel.INFO))
                                    .addLast("http", new HttpServerCodec())
                                    .addLast("aggregator", new HttpObjectAggregator(1048576))
                                    .addLast("front", new FrontHandler(clientSslContext));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(8080).sync();
            System.out.println("server is up on port: " + 8080);
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    static List<InputStream> getKeyAndCert(String[] args) throws FileNotFoundException {
        if (args.length == 0) {
            return List.of(
                    Application.class.getClassLoader().getResourceAsStream("cert.pem"),
                    Application.class.getClassLoader().getResourceAsStream("key.pem"));
        } else if (args.length == 2) {
            return List.of(new FileInputStream(args[0]), new FileInputStream(args[1]));
        }
        throw new RuntimeException(
                "command arg number should be two, first should be certificate path, the second should be key path");
    }
}
