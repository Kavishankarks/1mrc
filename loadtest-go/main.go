package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math"
	"net/http"
	"os"
	"sort"
	"sync"
	"sync/atomic"
	"time"
)

// Event represents the payload sent to /event
type Event struct {
	UserID string  `json:"userId"`
	Value  float64 `json:"value"`
}

// Stats represents the response from /stats
type Stats struct {
	TotalRequests int64   `json:"totalRequests"`
	UniqueUsers   int64   `json:"uniqueUsers"`
	Sum           float64 `json:"sum"`
	Avg           float64 `json:"avg"`
}

// Metrics tracks load test metrics
type Metrics struct {
	totalRequests   int64
	successRequests int64
	failedRequests  int64
	latencies       []time.Duration
	latenciesMu     sync.Mutex
	startTime       time.Time
	endTime         time.Time
	peakRPS         int64
	rpsHistory      []int64
	rpsHistoryMu    sync.Mutex
}

// Config holds load test configuration
type Config struct {
	targetURL      string
	totalRequests  int
	workers        int
	rps            int
	userPoolSize   int
	timeout        time.Duration
	showProgress   bool
	validateStats  bool
}

// Colors for output
const (
	colorReset  = "\033[0m"
	colorRed    = "\033[31m"
	colorGreen  = "\033[32m"
	colorYellow = "\033[33m"
	colorBlue   = "\033[34m"
	colorPurple = "\033[35m"
	colorCyan   = "\033[36m"
	colorWhite  = "\033[37m"
	colorBold   = "\033[1m"
)

func main() {
	config := parseFlags()

	printBanner(config)

	// Health check
	if !healthCheck(config.targetURL, config.timeout) {
		fmt.Printf("%s❌ Server is not responding at %s%s\n", colorRed, config.targetURL, colorReset)
		os.Exit(1)
	}
	fmt.Printf("%s✓ Server is healthy%s\n\n", colorGreen, colorReset)

	// Run load test
	metrics := runLoadTest(config)

	// Print results
	printResults(config, metrics)

	// Validate stats if requested
	if config.validateStats {
		validateServerStats(config, metrics)
	}
}

func parseFlags() *Config {
	config := &Config{}

	flag.StringVar(&config.targetURL, "url", "http://localhost:8080", "Target server URL")
	flag.IntVar(&config.totalRequests, "n", 1000000, "Total number of requests")
	flag.IntVar(&config.workers, "workers", 500, "Number of concurrent workers")
	flag.IntVar(&config.rps, "rps", 0, "Target requests per second (0 = unlimited)")
	flag.IntVar(&config.userPoolSize, "users", 75000, "User pool size for unique users")
	flag.DurationVar(&config.timeout, "timeout", 10*time.Second, "HTTP request timeout")
	flag.BoolVar(&config.showProgress, "progress", true, "Show progress during test")
	flag.BoolVar(&config.validateStats, "validate", true, "Validate server stats after test")

	flag.Parse()

	return config
}

func printBanner(config *Config) {
	fmt.Printf("%s╔═══════════════════════════════════════════════════════════════╗%s\n", colorBlue, colorReset)
	fmt.Printf("%s║     One Million Request Challenge - Go Load Tester           ║%s\n", colorBlue, colorReset)
	fmt.Printf("%s╠═══════════════════════════════════════════════════════════════╣%s\n", colorBlue, colorReset)
	fmt.Printf("%s║  Target URL:          %-39s ║%s\n", colorBlue, config.targetURL, colorReset)
	fmt.Printf("%s║  Total Requests:      %-39s ║%s\n", colorBlue, formatNumber(config.totalRequests), colorReset)
	fmt.Printf("%s║  Workers:             %-39d ║%s\n", colorBlue, config.workers, colorReset)
	if config.rps > 0 {
		fmt.Printf("%s║  Target RPS:          %-39s ║%s\n", colorBlue, formatNumber(config.rps), colorReset)
		duration := config.totalRequests / config.rps
		fmt.Printf("%s║  Expected Duration:   ~%-38ds ║%s\n", colorBlue, duration, colorReset)
	} else {
		fmt.Printf("%s║  Target RPS:          %-39s ║%s\n", colorBlue, "Unlimited", colorReset)
	}
	fmt.Printf("%s║  User Pool:           %-39s ║%s\n", colorBlue, formatNumber(config.userPoolSize), colorReset)
	fmt.Printf("%s╚═══════════════════════════════════════════════════════════════╝%s\n\n", colorBlue, colorReset)
}

func healthCheck(baseURL string, timeout time.Duration) bool {
	client := &http.Client{Timeout: timeout}

	// Try /health first
	resp, err := client.Get(baseURL + "/health")
	if err == nil && resp.StatusCode == http.StatusOK {
		resp.Body.Close()
		return true
	}
	if resp != nil {
		resp.Body.Close()
	}

	// Fallback to /stats
	resp, err = client.Get(baseURL + "/stats")
	if err == nil && resp.StatusCode == http.StatusOK {
		resp.Body.Close()
		return true
	}
	if resp != nil {
		resp.Body.Close()
	}

	return false
}

func runLoadTest(config *Config) *Metrics {
	metrics := &Metrics{
		latencies: make([]time.Duration, 0, config.totalRequests),
		startTime: time.Now(),
	}

	// Create HTTP client with connection pooling
	client := &http.Client{
		Timeout: config.timeout,
		Transport: &http.Transport{
			MaxIdleConns:        config.workers,
			MaxIdleConnsPerHost: config.workers,
			MaxConnsPerHost:     config.workers,
			IdleConnTimeout:     90 * time.Second,
		},
	}

	// Work queue
	workQueue := make(chan int, config.workers*2)

	// Wait group for workers
	var wg sync.WaitGroup

	// Rate limiter
	var rateLimiter <-chan time.Time
	if config.rps > 0 {
		ticker := time.NewTicker(time.Second / time.Duration(config.rps))
		defer ticker.Stop()
		rateLimiter = ticker.C
	}

	// Start workers
	for i := 0; i < config.workers; i++ {
		wg.Add(1)
		go worker(client, config, workQueue, metrics, &wg)
	}

	// Progress reporter
	var progressWg sync.WaitGroup
	if config.showProgress {
		progressWg.Add(1)
		go progressReporter(metrics, config.totalRequests, &progressWg)
	}

	// Send work
	fmt.Printf("%sStarting load test...%s\n\n", colorYellow, colorReset)

	for i := 0; i < config.totalRequests; i++ {
		if config.rps > 0 {
			<-rateLimiter
		}
		workQueue <- i
	}
	close(workQueue)

	// Wait for all workers to finish
	wg.Wait()
	metrics.endTime = time.Now()

	// Stop progress reporter
	if config.showProgress {
		progressWg.Wait()
	}

	return metrics
}

func worker(client *http.Client, config *Config, workQueue <-chan int, metrics *Metrics, wg *sync.WaitGroup) {
	defer wg.Done()

	for requestNum := range workQueue {
		event := Event{
			UserID: getUserID(requestNum, config.userPoolSize),
			Value:  float64(requestNum%1000) + 0.5,
		}

		payload, err := json.Marshal(event)
		if err != nil {
			atomic.AddInt64(&metrics.failedRequests, 1)
			continue
		}

		start := time.Now()
		resp, err := client.Post(config.targetURL+"/event", "application/json", bytes.NewReader(payload))
		latency := time.Since(start)

		atomic.AddInt64(&metrics.totalRequests, 1)

		if err != nil || resp.StatusCode != http.StatusOK {
			atomic.AddInt64(&metrics.failedRequests, 1)
			if resp != nil {
				io.Copy(io.Discard, resp.Body)
				resp.Body.Close()
			}
		} else {
			atomic.AddInt64(&metrics.successRequests, 1)
			io.Copy(io.Discard, resp.Body)
			resp.Body.Close()

			// Record latency (sampling to avoid memory issues with 1M entries)
			if requestNum%100 == 0 {
				metrics.latenciesMu.Lock()
				metrics.latencies = append(metrics.latencies, latency)
				metrics.latenciesMu.Unlock()
			}
		}
	}
}

func progressReporter(metrics *Metrics, total int, wg *sync.WaitGroup) {
	defer wg.Done()

	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	lastCount := int64(0)
	lastTime := time.Now()

	for range ticker.C {
		current := atomic.LoadInt64(&metrics.totalRequests)
		if current >= int64(total) {
			break
		}

		now := time.Now()
		elapsed := now.Sub(lastTime).Seconds()
		currentRPS := int64(float64(current-lastCount) / elapsed)

		// Track peak RPS
		if currentRPS > atomic.LoadInt64(&metrics.peakRPS) {
			atomic.StoreInt64(&metrics.peakRPS, currentRPS)
		}

		// Store RPS history
		metrics.rpsHistoryMu.Lock()
		metrics.rpsHistory = append(metrics.rpsHistory, currentRPS)
		metrics.rpsHistoryMu.Unlock()

		percentage := float64(current) / float64(total) * 100
		fmt.Printf("\r%sProgress: %s/%s (%.1f%%) | Current RPS: %s | Elapsed: %s%s",
			colorCyan,
			formatNumber(int(current)),
			formatNumber(total),
			percentage,
			formatNumber(int(currentRPS)),
			time.Since(metrics.startTime).Round(time.Second),
			colorReset,
		)

		lastCount = current
		lastTime = now
	}
	fmt.Println() // New line after progress
}

func printResults(config *Config, metrics *Metrics) {
	duration := metrics.endTime.Sub(metrics.startTime)
	avgRPS := float64(metrics.totalRequests) / duration.Seconds()
	successRate := float64(metrics.successRequests) / float64(metrics.totalRequests) * 100
	errorRate := float64(metrics.failedRequests) / float64(metrics.totalRequests) * 100

	fmt.Printf("\n%s╔═══════════════════════════════════════════════════════════════╗%s\n", colorGreen, colorReset)
	fmt.Printf("%s║                    Load Test Results                          ║%s\n", colorGreen, colorReset)
	fmt.Printf("%s╠═══════════════════════════════════════════════════════════════╣%s\n", colorGreen, colorReset)
	fmt.Printf("%s║  Duration:            %-39s ║%s\n", colorGreen, duration.Round(time.Millisecond), colorReset)
	fmt.Printf("%s║  Total Requests:      %-39s ║%s\n", colorGreen, formatNumber(int(metrics.totalRequests)), colorReset)
	fmt.Printf("%s║  Successful:          %-39s ║%s\n", colorGreen, formatNumber(int(metrics.successRequests)), colorReset)
	fmt.Printf("%s║  Failed:              %-39s ║%s\n", colorGreen, formatNumber(int(metrics.failedRequests)), colorReset)
	fmt.Printf("%s║  Success Rate:        %-38.2f%% ║%s\n", colorGreen, successRate, colorReset)

	errorColor := colorGreen
	if errorRate > 1.0 {
		errorColor = colorRed
	}
	fmt.Printf("%s║  Error Rate:          %s%-38.2f%%%s ║%s\n", colorGreen, errorColor, errorRate, colorGreen, colorReset)
	fmt.Printf("%s╠═══════════════════════════════════════════════════════════════╣%s\n", colorGreen, colorReset)

	// Throughput statistics
	fmt.Printf("%s║  Throughput Statistics:                                       ║%s\n", colorGreen, colorReset)
	fmt.Printf("%s║    Average RPS:     %-39s ║%s\n", colorGreen, formatNumber(int(avgRPS)), colorReset)

	peakRPS := atomic.LoadInt64(&metrics.peakRPS)
	if peakRPS > 0 {
		fmt.Printf("%s║    Peak RPS:        %-39s ║%s\n", colorGreen, formatNumber(int(peakRPS)), colorReset)
	}

	// Calculate min RPS from history
	metrics.rpsHistoryMu.Lock()
	if len(metrics.rpsHistory) > 0 {
		minRPS := metrics.rpsHistory[0]
		for _, rps := range metrics.rpsHistory {
			if rps < minRPS && rps > 0 {
				minRPS = rps
			}
		}
		fmt.Printf("%s║    Min RPS:         %-39s ║%s\n", colorGreen, formatNumber(int(minRPS)), colorReset)
	}
	metrics.rpsHistoryMu.Unlock()

	fmt.Printf("%s╠═══════════════════════════════════════════════════════════════╣%s\n", colorGreen, colorReset)

	// Latency statistics
	if len(metrics.latencies) > 0 {
		sort.Slice(metrics.latencies, func(i, j int) bool {
			return metrics.latencies[i] < metrics.latencies[j]
		})

		min := metrics.latencies[0]
		max := metrics.latencies[len(metrics.latencies)-1]
		avg := calculateAverage(metrics.latencies)
		p50 := percentile(metrics.latencies, 50)
		p90 := percentile(metrics.latencies, 90)
		p95 := percentile(metrics.latencies, 95)
		p99 := percentile(metrics.latencies, 99)

		fmt.Printf("%s║  Latency Statistics (sampled):                                ║%s\n", colorGreen, colorReset)
		fmt.Printf("%s║    Min:             %-39s ║%s\n", colorGreen, min.Round(time.Microsecond), colorReset)
		fmt.Printf("%s║    Avg:             %-39s ║%s\n", colorGreen, avg.Round(time.Microsecond), colorReset)
		fmt.Printf("%s║    P50:             %-39s ║%s\n", colorGreen, p50.Round(time.Microsecond), colorReset)
		fmt.Printf("%s║    P90:             %-39s ║%s\n", colorGreen, p90.Round(time.Microsecond), colorReset)
		fmt.Printf("%s║    P95:             %-39s ║%s\n", colorGreen, p95.Round(time.Microsecond), colorReset)
		fmt.Printf("%s║    P99:             %-39s ║%s\n", colorGreen, p99.Round(time.Microsecond), colorReset)
		fmt.Printf("%s║    Max:             %-39s ║%s\n", colorGreen, max.Round(time.Microsecond), colorReset)
	}

	fmt.Printf("%s╚═══════════════════════════════════════════════════════════════╝%s\n\n", colorGreen, colorReset)
}

func validateServerStats(config *Config, metrics *Metrics) {
	fmt.Printf("%sFetching server statistics...%s\n\n", colorYellow, colorReset)

	// Wait a moment for server to process any pending requests
	time.Sleep(2 * time.Second)

	client := &http.Client{Timeout: config.timeout}
	resp, err := client.Get(config.targetURL + "/stats")
	if err != nil {
		fmt.Printf("%s❌ Failed to fetch server stats: %v%s\n", colorRed, err, colorReset)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		fmt.Printf("%s❌ Server returned status %d%s\n", colorRed, resp.StatusCode, colorReset)
		return
	}

	var stats Stats
	if err := json.NewDecoder(resp.Body).Decode(&stats); err != nil {
		fmt.Printf("%s❌ Failed to parse server stats: %v%s\n", colorRed, err, colorReset)
		return
	}

	// Print server statistics
	fmt.Printf("%s╔═══════════════════════════════════════════════════════════════╗%s\n", colorBlue, colorReset)
	fmt.Printf("%s║                    Server Statistics                          ║%s\n", colorBlue, colorReset)
	fmt.Printf("%s╠═══════════════════════════════════════════════════════════════╣%s\n", colorBlue, colorReset)
	fmt.Printf("%s║  Total Requests:      %-39s ║%s\n", colorBlue, formatNumber(int(stats.TotalRequests)), colorReset)
	fmt.Printf("%s║  Unique Users:        %-39s ║%s\n", colorBlue, formatNumber(int(stats.UniqueUsers)), colorReset)
	fmt.Printf("%s║  Sum:                 %-39.2f ║%s\n", colorBlue, stats.Sum, colorReset)
	fmt.Printf("%s║  Average:             %-39.4f ║%s\n", colorBlue, stats.Avg, colorReset)
	fmt.Printf("%s╚═══════════════════════════════════════════════════════════════╝%s\n\n", colorBlue, colorReset)

	// Validate results
	expectedRequests := int64(config.totalRequests)
	tolerance := float64(expectedRequests) * 0.01 // 1% tolerance

	if math.Abs(float64(stats.TotalRequests-expectedRequests)) <= tolerance {
		fmt.Printf("%s✅ SUCCESS: Server processed %s requests (expected %s)%s\n",
			colorGreen, formatNumber(int(stats.TotalRequests)), formatNumber(config.totalRequests), colorReset)
	} else {
		fmt.Printf("%s⚠️  WARNING: Server processed %s requests (expected %s)%s\n",
			colorYellow, formatNumber(int(stats.TotalRequests)), formatNumber(config.totalRequests), colorReset)
	}

	// Validate aggregation correctness
	expectedAvg := stats.Sum / float64(stats.TotalRequests)
	if math.Abs(expectedAvg-stats.Avg) < 0.01 {
		fmt.Printf("%s✅ Aggregation is correct (avg: %.4f)%s\n", colorGreen, stats.Avg, colorReset)
	} else {
		fmt.Printf("%s❌ Aggregation error detected (expected %.4f, got %.4f)%s\n",
			colorRed, expectedAvg, stats.Avg, colorReset)
	}

	// Validate unique users
	expectedUsers := int64(config.userPoolSize)
	if expectedUsers > expectedRequests {
		expectedUsers = expectedRequests
	}

	userTolerance := float64(expectedUsers) * 0.05 // 5% tolerance for users
	if math.Abs(float64(stats.UniqueUsers-expectedUsers)) <= userTolerance {
		fmt.Printf("%s✅ Unique users count is correct (%s users)%s\n",
			colorGreen, formatNumber(int(stats.UniqueUsers)), colorReset)
	} else {
		fmt.Printf("%s⚠️  Unique users: %s (expected ~%s)%s\n",
			colorYellow, formatNumber(int(stats.UniqueUsers)), formatNumber(int(expectedUsers)), colorReset)
	}

	fmt.Println()
}

// Utility functions

func getUserID(requestNum, poolSize int) string {
	return fmt.Sprintf("user_%d", requestNum%poolSize)
}

func formatNumber(n int) string {
	if n < 1000 {
		return fmt.Sprintf("%d", n)
	}
	if n < 1000000 {
		return fmt.Sprintf("%d,%03d", n/1000, n%1000)
	}
	return fmt.Sprintf("%d,%03d,%03d", n/1000000, (n/1000)%1000, n%1000)
}

func calculateAverage(durations []time.Duration) time.Duration {
	var total time.Duration
	for _, d := range durations {
		total += d
	}
	return total / time.Duration(len(durations))
}

func percentile(durations []time.Duration, p int) time.Duration {
	if len(durations) == 0 {
		return 0
	}
	index := int(float64(len(durations)) * float64(p) / 100.0)
	if index >= len(durations) {
		index = len(durations) - 1
	}
	return durations[index]
}