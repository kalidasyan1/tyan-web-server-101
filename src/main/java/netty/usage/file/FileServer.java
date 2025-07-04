package netty.usage.file;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Simple File Server that serves static files from the current directory
 */
public class FileServer {
    private final int port;

    public FileServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new ChunkedWriteHandler())
                                .addLast(new FileServerHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("File Server started on http://localhost:" + port);
            System.out.println("Serving files from: " + System.getProperty("user.dir"));

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8085;
        new FileServer(port).start();
    }

    private static class FileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            if (!request.decoderResult().isSuccess()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            if (request.method() != HttpMethod.GET) {
                sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                return;
            }

            String uri = request.uri();
            if (uri.equals("/")) {
                // Try to serve index.html from resources/webpages first
                try {
                    InputStream indexStream = getClass().getClassLoader().getResourceAsStream("webpages/index.html");
                    if (indexStream != null) {
                        byte[] content = indexStream.readAllBytes();
                        indexStream.close();

                        FullHttpResponse response = new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                Unpooled.copiedBuffer(content));

                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);

                        if (HttpUtil.isKeepAlive(request)) {
                            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                        }

                        ctx.writeAndFlush(response);
                        return;
                    }
                } catch (Exception e) {
                    // Fall through to directory listing
                }

                // If index.html not found in resources, show directory listing
                sendDirectoryListing(ctx, ".");
                return;
            }

            // Handle webpages from resources
            if (uri.startsWith("/") && uri.contains(".html")) {
                String resourcePath = "webpages" + uri;
                try {
                    InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                    if (resourceStream != null) {
                        byte[] content = resourceStream.readAllBytes();
                        resourceStream.close();

                        FullHttpResponse response = new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                Unpooled.copiedBuffer(content));

                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);

                        if (HttpUtil.isKeepAlive(request)) {
                            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                        }

                        ctx.writeAndFlush(response);
                        return;
                    }
                } catch (Exception e) {
                    // Fall through to file system
                }
            }

            // Handle directory listing request
            if (uri.equals("/files")) {
                sendDirectoryListing(ctx, ".");
                return;
            }

            // Remove leading slash and sanitize path
            String path = uri.substring(1);
            path = path.replaceAll("/+", "/");

            File file = new File(path);
            if (!file.exists() || file.isHidden() || !file.isFile()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }

            RandomAccessFile raf = new RandomAccessFile(file, "r");
            long fileLength = raf.length();

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpUtil.setContentLength(response, fileLength);

            // Set content type based on file extension
            String contentType = getContentType(file.getName());
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);

            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            ctx.write(response);
            ctx.write(new ChunkedFile(raf, 0, fileLength, 8192));

            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!HttpUtil.isKeepAlive(request)) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer("Error: " + status.toString(), CharsetUtil.UTF_8));

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private String getContentType(String fileName) {
            if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                return "text/html; charset=UTF-8";
            } else if (fileName.endsWith(".css")) {
                return "text/css; charset=UTF-8";
            } else if (fileName.endsWith(".js")) {
                return "application/javascript; charset=UTF-8";
            } else if (fileName.endsWith(".json")) {
                return "application/json; charset=UTF-8";
            } else if (fileName.endsWith(".png")) {
                return "image/png";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                return "image/gif";
            } else {
                return "application/octet-stream";
            }
        }

        private void sendDirectoryListing(ChannelHandlerContext ctx, String dir) {
            File directory = new File(dir);
            File[] files = directory.listFiles();

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>");
            html.append("<html><head><title>Directory listing</title></head><body>");
            html.append("<h3>Directory: ").append(directory.getAbsolutePath()).append("</h3>");
            html.append("<ul>");

            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (file.isDirectory()) {
                        html.append("<li><a href=\"").append(fileName).append("/\">").append(fileName).append("/</a></li>");
                    } else {
                        html.append("<li><a href=\"").append(fileName).append("\">").append(fileName).append("</a></li>");
                    }
                }
            }

            html.append("</ul>");
            html.append("</body></html>");

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(html.toString(), CharsetUtil.UTF_8));

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            if (ctx.channel().isActive()) {
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }
}
