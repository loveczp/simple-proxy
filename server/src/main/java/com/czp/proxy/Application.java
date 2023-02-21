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
import java.util.HashMap;
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
            SslContext serverSslContext = SslContextBuilder.forServer(
                            (InputStream) paras.get("cert"), (InputStream) paras.get("key"))
                    .build();
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

            ChannelFuture f = b.bind((Integer) paras.get("port")).sync();
            System.out.println("server is up on port: " + paras.get("port"));
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    static Map<String, Object> parseParas(String[] args) throws FileNotFoundException, ParseException {

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
        Option port = Option.builder()
                .longOpt("port")
                .argName("port")
                .hasArg()
                .desc("set the server port")
                .build();
        options.addOption(cert);
        options.addOption(key);
        options.addOption(port);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        Map<String, Object> returnArgs = new HashMap<>();
        if ((cmd.hasOption("cert") && cmd.hasOption("key") == false)
                || (cmd.hasOption("cert") == false && cmd.hasOption("key"))) {
            throw new RuntimeException("cert and key should either be both set or be not set");
        } else {
            if (cmd.hasOption("cert")) {
                returnArgs.put("cert", new FileInputStream(cmd.getOptionValue("cert")));
                returnArgs.put("key", new FileInputStream(cmd.getOptionValue("key")));
            } else {
                returnArgs.put("cert", Application.class.getClassLoader().getResourceAsStream("cert.pem"));
                returnArgs.put("key", Application.class.getClassLoader().getResourceAsStream("key.pem"));
            }
        }

        if (cmd.hasOption("port")) {
            returnArgs.put("port", Integer.valueOf(cmd.getOptionValue("port")));
        } else {
            returnArgs.put("port", 8080);
        }

        return returnArgs;
    }
}
