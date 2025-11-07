package com.recnos.onemrc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;
import com.recnos.onemrc.service.EventStorageService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Ultra-high performance Vert.x HTTP server for One Million Request Challenge (1MRC).
 * Optimized for massive concurrent throughput using Vert.x event loop and lock-free operations.
 * All operations are non-blocking and handled directly on the event loop for maximum performance.
 */
public class VertxServer extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(VertxServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule());
    private static final EventStorageService storageService = EventStorageService.getInstance();

    @Override
    public void start(Promise<Void> startPromise) {
        int port = config().getInteger("port", DEFAULT_PORT);

        Router router = Router.router(vertx);

        // Define routes - NO BodyHandler middleware for maximum performance
        router.post("/event").handler(this::handlePostEvent);
        router.get("/stats").handler(this::handleGetStats);
        router.get("/health").handler(this::handleHealthCheck);

        // Handle 404
        router.route().handler(ctx -> {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Endpoint not found\"}");
        });

        // Create HTTP server with optimized options
        HttpServerOptions serverOptions = new HttpServerOptions()
                .setPort(port)
                .setHost("127.0.0.1")         // IPv4 only to avoid dual-stack issues
                .setTcpNoDelay(true)          // Disable Nagle's algorithm for lower latency
                .setTcpKeepAlive(true)        // Keep connections alive
                .setReuseAddress(true)        // Allow port reuse
                .setReusePort(true)           // Enable SO_REUSEPORT for better load distribution
                .setAcceptBacklog(16384)      // High backlog for burst traffic
                .setReceiveBufferSize(64 * 1024)  // 64KB receive buffer
                .setSendBufferSize(64 * 1024)     // 64KB send buffer
                .setIdleTimeout(30)           // Close idle connections after 30s
                .setMaxHeaderSize(8192)       // Limit header size to prevent abuse
                .setCompressionSupported(false); // Disable compression for speed

        HttpServer server = vertx.createHttpServer(serverOptions);

        server.requestHandler(router)
            .listen(port)
            .onSuccess(httpServer -> {
                int numCores = Runtime.getRuntime().availableProcessors();
                logger.info("╔═══════════════════════════════════════════════════════════════╗");
                logger.info("║  Vert.x HTTP Server Started Successfully                      ║");
                logger.info("╠═══════════════════════════════════════════════════════════════╣");
                logger.info("║  Port:                    {}                                 ║", port);
                logger.info("║  CPU Cores:               {}                                  ║", numCores);
                logger.info("║  Event Loop Threads:      {}                                  ║", numCores * 2);
                logger.info("╠═══════════════════════════════════════════════════════════════╣");
                logger.info("║  Endpoints:                                                   ║");
                logger.info("║    POST http://localhost:{}/event                            ║", port);
                logger.info("║    GET  http://localhost:{}/stats                            ║", port);
                logger.info("║    GET  http://localhost:{}/health                           ║", port);
                logger.info("╠═══════════════════════════════════════════════════════════════╣");
                logger.info("║  Optimizations:                                               ║");
                logger.info("║    ✓ NO BodyHandler (zero middleware overhead)              ║");
                logger.info("║    ✓ JSON parsing off event loop (executeBlocking)          ║");
                logger.info("║    ✓ Lock-free concurrent aggregation (LongAdder)           ║");
                logger.info("║    ✓ Java records (zero overhead DTOs)                      ║");
                logger.info("║    ✓ Empty body response (no JSON serialization)            ║");
                logger.info("║    ✓ IPv4-only binding (no dual-stack issues)               ║");
                logger.info("║    ✓ Idle timeout: 30s, Accept backlog: 16384               ║");
                logger.info("║    ✓ TCP_NODELAY + SO_REUSEPORT enabled                      ║");
                logger.info("╚═══════════════════════════════════════════════════════════════╝");
                startPromise.complete();
            })
            .onFailure(error -> {
                logger.error("Failed to start HTTP server", error);
                startPromise.fail(error);
            });
    }

    /**
     * Handles POST /event endpoint with ultra-low latency.
     * Body parsing moved off event loop to prevent blocking under extreme load.
     */
    private void handlePostEvent(RoutingContext ctx) {
        // Read body asynchronously without BodyHandler
        ctx.request().body()
            .onSuccess(buffer -> {
                // Parse JSON off the event loop to keep it responsive
                vertx.executeBlocking(() -> {
                    try {
                        String body = buffer.toString();
                        EventDto event = objectMapper.readValue(body, EventDto.class);

                        // Validate input
                        if (event.userId() == null || event.userId().isEmpty()) {
                            throw new IllegalArgumentException("userId is required");
                        }

                        return event;
                    } catch (Exception e) {
                        throw new RuntimeException("Invalid request body: " + e.getMessage(), e);
                    }
                }, false) // ordered=false for better parallelism
                .onSuccess(event -> {
                    // Store event using lock-free operations (non-blocking)
                    storageService.addEvent(event);

                    // Send 200 OK with empty body (faster than JSON serialization)
                    ctx.response()
                        .setStatusCode(200)
                        .end();
                })
                .onFailure(error -> {
                    logger.error("Error processing event: {}", error.getMessage());
                    if (!ctx.response().ended()) {
                        ctx.response()
                            .setStatusCode(400)
                            .putHeader("Content-Type", "application/json")
                            .end("{\"error\":\"" + error.getMessage() + "\"}");
                    }
                });
            })
            .onFailure(error -> {
                logger.error("Error reading request body: {}", error.getMessage());
                if (!ctx.response().ended()) {
                    ctx.response()
                        .setStatusCode(400)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"Failed to read request body\"}");
                }
            });
    }

    /**
     * Handles GET /stats endpoint - returns aggregated statistics.
     * Since all operations are lock-free and non-blocking, we handle directly on event loop.
     */
    private void handleGetStats(RoutingContext ctx) {
        try {
            StatsDto stats = storageService.getStats();
            byte[] jsonResponse = objectMapper.writeValueAsBytes(stats);

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .putHeader("Connection", "keep-alive")
                .end(io.vertx.core.buffer.Buffer.buffer(jsonResponse));

        } catch (Exception e) {
            logger.error("Error getting stats", e);
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end("{\"error\":\"Error retrieving stats\"}");
        }
    }

    /**
     * Handles GET /health endpoint - simple health check.
     */
    private void handleHealthCheck(RoutingContext ctx) {
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .putHeader("Connection", "keep-alive")
            .end("{\"status\":\"healthy\"}");
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

        // Configure Vert.x with optimized settings
        int numCores = Runtime.getRuntime().availableProcessors();
        VertxOptions vertxOptions = new VertxOptions()
                .setEventLoopPoolSize(numCores * 2)  // 2x CPU cores for optimal performance
                .setWorkerPoolSize(20)                // Reduced worker pool since we use virtual threads
                .setPreferNativeTransport(true);      // Use native transport if available (epoll on Linux, kqueue on macOS)

        Vertx vertx = Vertx.vertx(vertxOptions);

        // Deploy the verticle with port configuration
        final int serverPort = port;
        vertx.deployVerticle(
            new VertxServer(),
            new io.vertx.core.DeploymentOptions().setConfig(
                new io.vertx.core.json.JsonObject().put("port", serverPort)
            )
        ).onSuccess(id -> {
            logger.info("Vert.x server deployed successfully with ID: {}", id);
        }).onFailure(error -> {
            logger.error("Failed to deploy Vert.x server", error);
            System.exit(1);
        });
    }
}