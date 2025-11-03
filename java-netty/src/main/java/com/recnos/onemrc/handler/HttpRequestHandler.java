package com.recnos.onemrc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;
import com.recnos.onemrc.service.EventStorageService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Ultra-high performance HTTP request handler using Netty and Java Virtual Threads.
 * Processes POST /event and GET /stats with minimal latency.
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final EventStorageService storageService = EventStorageService.getInstance();

    // Virtual thread executor for handling business logic without blocking event loop
    private static final ExecutorService virtualThreadExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // CRITICAL: Extract data from ByteBuf BEFORE offloading to virtual threads
        // Netty will release the ByteBuf after this method returns
        String uri = request.uri();
        HttpMethod method = request.method();
        String content = request.content().toString(CharsetUtil.UTF_8);

        // Now offload to virtual threads with extracted data
        virtualThreadExecutor.submit(() -> handleRequest(ctx, uri, method, content));
    }

    private void handleRequest(ChannelHandlerContext ctx, String uri, HttpMethod method, String content) {
        try {
            if ("/event".equals(uri) && method == HttpMethod.POST) {
                handlePostEvent(ctx, content);
            } else if ("/stats".equals(uri) && method == HttpMethod.GET) {
                handleGetStats(ctx);
            } else if ("/health".equals(uri) && method == HttpMethod.GET) {
                handleHealthCheck(ctx);
            } else {
                sendErrorResponse(ctx, NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling request", e);
            sendErrorResponse(ctx, INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * Handles POST /event endpoint with ultra-low latency.
     */
    private void handlePostEvent(ChannelHandlerContext ctx, String content) {
        try {
            // Parse JSON request body
            EventDto event = objectMapper.readValue(content, EventDto.class);

            // Validate input
            if (event.getUserId() == null || event.getUserId().isEmpty()) {
                sendErrorResponse(ctx, BAD_REQUEST, "userId is required");
                return;
            }

            // Store event using lock-free operations
            storageService.addEvent(event);

            // Send 200 OK response
            sendJsonResponse(ctx, OK, "{\"status\":\"ok\"}");

        } catch (Exception e) {
            logger.error("Error processing event", e);
            sendErrorResponse(ctx, BAD_REQUEST, "Invalid request body");
        }
    }

    /**
     * Handles GET /stats endpoint - returns aggregated statistics.
     */
    private void handleGetStats(ChannelHandlerContext ctx) {
        try {
            StatsDto stats = storageService.getStats();
            String jsonResponse = objectMapper.writeValueAsString(stats);
            sendJsonResponse(ctx, OK, jsonResponse);
        } catch (Exception e) {
            logger.error("Error getting stats", e);
            sendErrorResponse(ctx, INTERNAL_SERVER_ERROR, "Error retrieving stats");
        }
    }

    /**
     * Handles GET /health endpoint - simple health check.
     */
    private void handleHealthCheck(ChannelHandlerContext ctx) {
        sendJsonResponse(ctx, OK, "{\"status\":\"healthy\"}");
    }

    /**
     * Sends JSON response with proper headers and connection handling.
     */
    private void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1,
                status,
                Unpooled.copiedBuffer(json, CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        ctx.writeAndFlush(response);
    }

    /**
     * Sends error response with proper status code.
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        String json = String.format("{\"error\":\"%s\"}", message);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1,
                status,
                Unpooled.copiedBuffer(json, CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in channel handler", cause);
        ctx.close();
    }
}