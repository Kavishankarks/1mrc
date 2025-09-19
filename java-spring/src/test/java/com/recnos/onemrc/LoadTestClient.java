package com.recnos.onemrc;

import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;
import com.fasterxml.jackson.databind.ObjectMapper;

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

public class LoadTestClient {
    
    private static final int TOTAL_REQUESTS = 1_000_000;
    private static final int CONCURRENCY = 100; // Optimized with connection reuse
    private static final String SERVER_URL = "http://localhost:8080";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private FileWriter logWriter;
    
    public LoadTestClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor()) // Use virtual threads for HTTP I/O
                .version(HttpClient.Version.HTTP_1_1) // Better connection reuse than HTTP/2 for this use case
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
            String logFileName = String.format("logs/1mrc_java_test_%s.log", timestamp);
            
            this.logWriter = new FileWriter(logFileName, true);
            log("Starting Java 1MRC test with " + TOTAL_REQUESTS + " requests and " + CONCURRENCY + " concurrent workers");
            
            System.out.printf("Starting Java 1MRC test with %d requests and %d concurrent workers%n", TOTAL_REQUESTS, CONCURRENCY);
            System.out.printf("Java version: %s%n", System.getProperty("java.version"));
            System.out.printf("Available processors: %d%n", Runtime.getRuntime().availableProcessors());
            System.out.printf("Log file: %s%n", logFileName);
            
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
        
        // Use virtual threads for massive concurrency (Java 21+)
        ExecutorService executor;
        try {
            // Try virtual threads first (Java 21+)
            executor = Executors.newVirtualThreadPerTaskExecutor();
            System.out.println("Using Virtual Threads for maximum concurrency");
            log("Using Virtual Threads for maximum concurrency");
        } catch (Exception e) {
            // Fallback to platform threads
            executor = Executors.newFixedThreadPool(CONCURRENCY);
            System.out.println("Using Platform Threads (Virtual Threads not available)");
            log("Using Platform Threads (Virtual Threads not available)");
        }
        
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        log("Starting load test execution");
        
        // Reset server counters before test
        try {
            resetServer();
            log("Server counters reset successfully");
        } catch (Exception e) {
            log("Failed to reset server: " + e.getMessage());
        }
        
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
                        System.out.printf("Completed: %d/%d (%.1f req/s)%n", completed, TOTAL_REQUESTS, rate);
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
            
            System.out.printf("%n=== Test Results ===%n");
            System.out.printf("Total time: %.6fs%n", totalTime / 1000.0);
            System.out.printf("Requests per second: %.2f%n", rps);
            System.out.printf("Errors: %d%n", errorCount.get());
            
            log(String.format("Test completed - Time: %d ms, RPS: %.2f, Errors: %d", totalTime, rps, errorCount.get()));
            
            // Wait a bit for server to process all requests
            Thread.sleep(100);
            
            // Get final stats
            StatsDto stats = getStats();
            if (stats != null) {
                System.out.printf("%n=== Server Stats ===%n");
                System.out.printf("Total Requests: %d%n", stats.getTotalRequests());
                System.out.printf("Unique Users: %d%n", stats.getUniqueUsers());
                System.out.printf("Sum: %.2f%n", stats.getSum());
                System.out.printf("Average: %.2f%n", stats.getAvg());
                
                log(String.format("Server stats - Total: %d, Users: %d, Sum: %.2f, Avg: %.2f",
                    stats.getTotalRequests(), stats.getUniqueUsers(), stats.getSum(), stats.getAvg()));
                
                if (stats.getTotalRequests() == TOTAL_REQUESTS) {
                    System.out.println("\n✅ SUCCESS: All requests processed correctly!");
                    log("SUCCESS: All " + TOTAL_REQUESTS + " requests processed correctly!");
                } else {
                    System.out.printf("\n❌ FAILED: Expected %d requests, got %d%n", TOTAL_REQUESTS, stats.getTotalRequests());
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
            // Exit cleanly after test completion
            System.exit(0);
        }
    }
    
    private void sendEvent(EventDto event) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(event);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/event"))
                .header("Content-Type", "application/json")
                // HttpClient automatically manages connection reuse with HTTP/1.1
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
    
    private void resetServer() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + "/reset"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to reset server: HTTP " + response.statusCode());
        }
    }
    
    public static void main(String[] args) {
        new LoadTestClient().runTest();
    }
}