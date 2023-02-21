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
import java.util.Map;
import javax.net.ssl.SSLException;

import org.apache.commons.cli.*;

public class Application {

    public static void main(String[] args) throws Exception {
        var paras = parseParas(args);
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            SslContext clientSslContext = SslContextBuilder.forClient().build();
            SslContext serverSslContext = SslContextBuilder.forServer(paras.get("cert"), paras.get("key")).build();
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

    static Map<String, InputStream> parseParas(String[] args) throws FileNotFoundException, ParseException {

        Options options = new Options();

        Option cert = Option.builder()
                .longOpt("cert")
                .argName("cert")
                .hasArg()
                .desc("set the path of certificate file")
                .build();

        Option key = Option.builder()
                .longOpt("key")
                .argName("key")
                .hasArg()
                .desc("set the path of key file")
                .build();
        options.addOption(cert);
        options.addOption(key);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        if ((cmd.hasOption("cert") && cmd.hasOption("key") == false)
                || (cmd.hasOption("cert") == false && cmd.hasOption("key"))) {
            System.out.println("cert and key should either be both set or be not set");
            throw new RuntimeException("cert and key should either be both set or be not set");
        } else {
            if (cmd.hasOption("cert")) {
                return Map.of("cert",
                        new FileInputStream(cmd.getOptionValue("cert")),
                        "key",
                        new FileInputStream(cmd.getOptionValue("key")));
            } else {
                return Map.of("cert",
                        Application.class.getClassLoader().getResourceAsStream("cert.pem"),
                        "key",
                        Application.class.getClassLoader().getResourceAsStream("key.pem"));
            }
        }
    }
}
