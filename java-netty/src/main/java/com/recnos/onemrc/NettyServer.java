package com.recnos.onemrc;

import com.recnos.onemrc.handler.HttpRequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ultra-high performance Netty HTTP server for One Million Request Challenge (1MRC).
 * Optimized for ultra-low latency and massive concurrent throughput.
 * Uses Java Virtual Threads (Project Loom) for business logic processing.
 */
public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_CONTENT_LENGTH = 65536; // 64KB

    private final int port;

    public NettyServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        // Boss group handles incoming connections
        // Worker group handles I/O operations
        // Using 2x CPU cores for optimal performance
        int numCores = Runtime.getRuntime().availableProcessors();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(numCores * 2);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // HTTP codec for request/response encoding
                            pipeline.addLast(new HttpServerCodec());

                            // Aggregate HTTP chunks into FullHttpRequest
                            pipeline.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));

                            // Our custom handler with virtual threads
                            pipeline.addLast(new HttpRequestHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 8192)  // Increased for high concurrency
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, 64 * 1024)  // Increased buffer
                    .childOption(ChannelOption.SO_SNDBUF, 64 * 1024)  // Increased buffer
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(32 * 1024, 64 * 1024));

            // Bind and start to accept incoming connections
            ChannelFuture future = bootstrap.bind(port).sync();

            logger.info("╔═══════════════════════════════════════════════════════════════╗");
            logger.info("║  Netty HTTP Server Started Successfully                       ║");
            logger.info("╠═══════════════════════════════════════════════════════════════╣");
            logger.info("║  Port:                    {}                                 ║", port);
            logger.info("║  CPU Cores:               {}                                  ║", numCores);
            logger.info("║  Worker Threads:          {}                                 ║", numCores * 2);
            logger.info("║  Virtual Threads:         Enabled (Project Loom)              ║");
            logger.info("║  Max Content Length:      {} KB                              ║", MAX_CONTENT_LENGTH / 1024);
            logger.info("╠═══════════════════════════════════════════════════════════════╣");
            logger.info("║  Endpoints:                                                   ║");
            logger.info("║    POST http://localhost:{}/event                            ║", port);
            logger.info("║    GET  http://localhost:{}/stats                            ║", port);
            logger.info("║    GET  http://localhost:{}/health                           ║", port);
            logger.info("╠═══════════════════════════════════════════════════════════════╣");
            logger.info("║  Optimizations:                                               ║");
            logger.info("║    ✓ Lock-free concurrent aggregation                        ║");
            logger.info("║    ✓ TCP_NODELAY enabled (Nagle disabled)                    ║");
            logger.info("║    ✓ SO_KEEPALIVE enabled                                    ║");
            logger.info("║    ✓ Connection pooling (SO_REUSEADDR)                       ║");
            logger.info("║    ✓ Virtual threads for non-blocking I/O                    ║");
            logger.info("╚═══════════════════════════════════════════════════════════════╝");

            // Wait until the server socket is closed
            future.channel().closeFuture().sync();

        } finally {
            // Graceful shutdown
            logger.info("Shutting down Netty server...");
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        // Allow port override via command line argument
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}. Using default port: {}", args[0], DEFAULT_PORT);
            }
        }

        // Check if virtual threads are available
        try {
            Thread.ofVirtual().start(() -> {}).join();
            logger.info("Java Virtual Threads (Project Loom) are available and enabled!");
        } catch (Exception e) {
            logger.warn("Virtual threads not available. Requires Java 21+ with Project Loom support.");
        }

        NettyServer server = new NettyServer(port);
        try {
            server.start();
        } catch (Exception e) {
            logger.error("Failed to start Netty server", e);
            System.exit(1);
        }
    }
}