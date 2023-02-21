package com.czp.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;

public class FrontHandler extends ChannelInboundHandlerAdapter {
    Channel backendChannel;
    SslContext clientSslContext;

    public FrontHandler(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws URISyntaxException {
        if (msg instanceof FullHttpRequest) {
            HttpRequest req = (FullHttpRequest) msg;
            String method = req.method().name();
            String hostport = req.headers().get("Host");
            String[] hostportArray = hostport.split(":");
            String host = hostportArray[0];
            Integer port = Integer.valueOf(hostportArray.length == 1 ? "80" : hostportArray[1]);
            if (method.equals("CONNECT") == true) {
                // https proxy
                createHttpsOutChannel(ctx, host, port);
            } else if (method.equals("GET") == true && req.uri().startsWith("/health")) {
                handleHealthRequest(ctx, req);
            } else if (req.uri().contains(hostport)) {
                // http proxy
                createHttpOutChannel(ctx, host, port, req);
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

    private void createHttpsOutChannel(ChannelHandlerContext ctx, String host, Integer port) {
        Bootstrap strap = new Bootstrap();
        strap.group(ctx.channel().eventLoop());
        strap.channel(NioSocketChannel.class);
        strap.option(ChannelOption.TCP_NODELAY, true);
        strap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast("back", new BackendHandler(ctx.channel()));
            }
        });
        ChannelFuture channelFuture = strap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener(f1 -> {
            var resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            ctx.writeAndFlush(resp).addListener(f2 -> {
                if (ctx.pipeline().get("http") != null) {
                    ctx.pipeline().remove("http");
                }
                if (ctx.pipeline().get("aggregator") != null) {
                    ctx.pipeline().remove("aggregator");
                }
            });
        });
        backendChannel = channelFuture.channel();
    }

    private void createHttpOutChannel(ChannelHandlerContext ctx, String host, Integer port, HttpRequest req) {
        Bootstrap strap = new Bootstrap();
        strap.group(ctx.channel().eventLoop());
        strap.channel(ctx.channel().getClass());
        strap.option(ChannelOption.TCP_NODELAY, true);
        strap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipe = ch.pipeline();
                if (req.uri().toLowerCase().startsWith("https")) {
                    pipe.addLast("ssl", clientSslContext.newHandler(ch.alloc()));
                }
                // pipe.addLast("log", new LoggingHandler(LogLevel.INFO));
                pipe.addLast("http", new HttpClientCodec());
                pipe.addLast("aggregator", new HttpObjectAggregator(1048576));
                pipe.addLast("back", new BackendHandler(ctx.channel()));
            }
        });
        ChannelFuture channelFuture = strap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener(x -> {
            channelFuture.channel().writeAndFlush(req);
        });
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
