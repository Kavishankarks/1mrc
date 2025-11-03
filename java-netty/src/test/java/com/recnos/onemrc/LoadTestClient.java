package com.recnos.onemrc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance load test client for the Netty 1MRC server.
 * Uses Java Virtual Threads for maximum concurrency.
 */
public class LoadTestClient {

    private static final int TOTAL_REQUESTS = 1_000_000;
    private static final int CONCURRENCY = 200; // Higher concurrency for Netty testing
    private static final String SERVER_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private FileWriter logWriter;

    public LoadTestClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor()) // Virtual threads for HTTP I/O
                .version(HttpClient.Version.HTTP_1_1) // Better connection reuse
                .build();

        this.objectMapper = new ObjectMapper();

        setupLogging();
    }

    private void setupLogging() {
        try {
            Path logsDir = Paths.get("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String logFileName = String.format("logs/1mrc_netty_test_%s.log", timestamp);

            this.logWriter = new FileWriter(logFileName, true);
            log("Starting Netty 1MRC test with " + TOTAL_REQUESTS + " requests and " + CONCURRENCY + " concurrent workers");

            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║        One Million Request Challenge - Load Test             ║");
            System.out.println("╠═══════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Total Requests:      %,15d                       ║%n", TOTAL_REQUESTS);
            System.out.printf("║  Concurrency:         %15d                       ║%n", CONCURRENCY);
            System.out.printf("║  Server URL:          %-31s║%n", SERVER_URL);
            System.out.printf("║  Java Version:        %-31s║%n", System.getProperty("java.version"));
            System.out.printf("║  CPU Cores:           %15d                       ║%n", Runtime.getRuntime().availableProcessors());
            System.out.printf("║  Log File:            %-31s║%n", logFileName);
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");

        } catch (IOException e) {
            throw new RuntimeException("Failed to setup logging", e);
        }
    }

    private void log(String message) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            logWriter.write(String.format("%s - %s%n", timestamp, message));
            logWriter.flush();
        } catch (IOException e) {
            System.err.println("Failed to write to log: " + e.getMessage());
        }
    }

    public void runTest() {
        long startTime = System.currentTimeMillis();

        // Use virtual threads for massive concurrency
        ExecutorService executor;
        try {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            System.out.println("\nUsing Virtual Threads for maximum concurrency\n");
            log("Using Virtual Threads for maximum concurrency");
        } catch (Exception e) {
            executor = Executors.newFixedThreadPool(CONCURRENCY);
            System.out.println("\nUsing Platform Threads (Virtual Threads not available)\n");
            log("Using Platform Threads (Virtual Threads not available)");
        }

        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

        log("Starting load test execution");
        System.out.println("Starting load test...\n");

        // Submit all requests
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    EventDto event = new EventDto(
                        "user_" + (requestId % 75000) + "_req_" + requestId,
                        (double) (requestId % 1000) + 0.5
                    );

                    sendEvent(event);

                    long completed = completedRequests.incrementAndGet();
                    if (completed % 100000 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double rate = completed * 1000.0 / elapsed;
                        System.out.printf("Progress: %,10d/%,d (%.1f req/s)%n", completed, TOTAL_REQUESTS, rate);
                        log(String.format("Progress: %d/%d requests completed (%.1f req/s)", completed, TOTAL_REQUESTS, rate));
                    }

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log("Request error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            executor.shutdown();

            long totalTime = System.currentTimeMillis() - startTime;
            double rps = TOTAL_REQUESTS * 1000.0 / totalTime;

            System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║                        Test Results                           ║");
            System.out.println("╠═══════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Total Time:          %15.6f seconds            ║%n", totalTime / 1000.0);
            System.out.printf("║  Requests/Second:     %,15.2f                    ║%n", rps);
            System.out.printf("║  Errors:              %,15d                    ║%n", errorCount.get());
            System.out.printf("║  Success Rate:        %15.2f%%                   ║%n",
                (TOTAL_REQUESTS - errorCount.get()) * 100.0 / TOTAL_REQUESTS);
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");

            log(String.format("Test completed - Time: %d ms, RPS: %.2f, Errors: %d", totalTime, rps, errorCount.get()));

            // Wait for server to process all requests
            Thread.sleep(200);

            // Get final stats
            StatsDto stats = getStats();
            if (stats != null) {
                System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
                System.out.println("║                      Server Statistics                        ║");
                System.out.println("╠═══════════════════════════════════════════════════════════════╣");
                System.out.printf("║  Total Requests:      %,15d                    ║%n", stats.getTotalRequests());
                System.out.printf("║  Unique Users:        %,15d                    ║%n", stats.getUniqueUsers());
                System.out.printf("║  Sum:                 %,15.2f                    ║%n", stats.getSum());
                System.out.printf("║  Average:             %15.2f                    ║%n", stats.getAvg());
                System.out.println("╚═══════════════════════════════════════════════════════════════╝");

                log(String.format("Server stats - Total: %d, Users: %d, Sum: %.2f, Avg: %.2f",
                    stats.getTotalRequests(), stats.getUniqueUsers(), stats.getSum(), stats.getAvg()));

                if (stats.getTotalRequests() == TOTAL_REQUESTS) {
                    System.out.println("\n✅ SUCCESS: All 1,000,000 requests processed correctly!");
                    log("SUCCESS: All " + TOTAL_REQUESTS + " requests processed correctly!");
                } else {
                    System.out.printf("\n❌ FAILED: Expected %,d requests, got %,d%n", TOTAL_REQUESTS, stats.getTotalRequests());
                    log(String.format("FAILED: Expected %d requests, got %d", TOTAL_REQUESTS, stats.getTotalRequests()));
                }
            }

        } catch (InterruptedException | IOException e) {
            log("Test interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            try {
                if (logWriter != null) {
                    logWriter.close();
                }
            } catch (IOException e) {
                System.err.println("Failed to close log file: " + e.getMessage());
            }
            System.exit(0);
        }
    }

    private void sendEvent(EventDto event) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(event);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/event"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private StatsDto getStats() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/stats"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), StatsDto.class);
        } else {
            log("Failed to get stats: HTTP " + response.statusCode());
            return null;
        }
    }

    public static void main(String[] args) {
        new LoadTestClient().runTest();
    }
}