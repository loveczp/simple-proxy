package com.czp.proxy;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
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
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            String method = req.method().name();
            String hostport = req.headers().get("Host");
            String[] hostportArray = hostport.split(":");
            String host = hostportArray[0];
            Integer port = Integer.valueOf(hostportArray.length == 1 ? "80" : hostportArray[1]);
            if (method.equals("CONNECT") == true) {
                //https proxy
                createHttpsOutChannel(ctx, host, port);
            } else if (method.equals("GET") == true && req.uri().startsWith("/health")) {
                handleHealthRequest(ctx, req);
            } else if (req.uri().contains(hostport)) {
                //http proxy
                System.out.println("orig req:\n" + req + "\n");
                String originalUri = req.uri();
                req.headers().remove(HttpHeaderNames.PROXY_CONNECTION);
                int hostIndex = originalUri.indexOf(hostport);
                String path = originalUri.substring(hostIndex + hostport.length());
                req.setUri(path);
                System.out.println("new req:\n" + req + "\n");
                createHttpOutChannel(ctx, host, port, req.uri(), req);
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
                ch.pipeline()
                        .addLast("back", new BackendHandler(ctx.channel()));
            }
        });
        ChannelFuture channelFuture = strap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener(f1 -> {
            var resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            ctx.writeAndFlush(resp).addListener(f2 -> ctx.pipeline().remove("http"));
        });
        backendChannel = channelFuture.channel();
    }


    private void createHttpOutChannel(ChannelHandlerContext ctx, String host, Integer port, String originalUri, HttpRequest req) {
        Bootstrap strap = new Bootstrap();
        strap.group(ctx.channel().eventLoop());
        strap.channel(ctx.channel().getClass());
        strap.option(ChannelOption.TCP_NODELAY, true);
        strap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipe = ch.pipeline();
                if (originalUri.toLowerCase().startsWith("https")) {
                    pipe.addLast("ssl", clientSslContext.newHandler(ch.alloc()));
                }
                pipe.addLast("log", new LoggingHandler(LogLevel.INFO));
                pipe.addLast("http", new HttpClientCodec());
                pipe.addLast("back", new BackendHandler(ctx.channel()));
            }
        });
        ChannelFuture channelFuture = strap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener(x -> {
            var writeFuture = channelFuture.channel().writeAndFlush(req);
            writeFuture.addListener(y -> channelFuture.channel().pipeline().remove("http"));
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
