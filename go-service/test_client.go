 package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"sync"
	"sync/atomic"
	"time"
)

type Event struct {
	UserID string  `json:"userId"`
	Value  float64 `json:"value"`
}

type Stats struct {
	TotalRequests int64   `json:"totalRequests"`
	UniqueUsers   int64   `json:"uniqueUsers"`
	Sum           float64 `json:"sum"`
	Avg           float64 `json:"avg"`
}

func main() {
	const (
		totalRequests = 1_000_000
		concurrency   = 1000
		serverURL     = "http://localhost:8080"
	)

	timestamp := time.Now().Format("20060102_150405")
	logFile, err := os.Create(filepath.Join("logs", fmt.Sprintf("1mrc_test_%s.log", timestamp)))
	if err != nil {
		log.Fatal(err)
	}
	defer logFile.Close()
	
	log.SetOutput(logFile)
	log.Printf("Starting 1MRC test with %d requests and %d concurrent workers", totalRequests, concurrency)

	fmt.Printf("Starting 1MRC test with %d requests and %d concurrent workers\n", totalRequests, concurrency)
	fmt.Printf("Go version: %s\n", runtime.Version())
	fmt.Printf("GOMAXPROCS: %d\n", runtime.GOMAXPROCS(0))
	fmt.Printf("Log file: %s\n", logFile.Name())

	client := &http.Client{
		Transport: &http.Transport{
			MaxIdleConns:        concurrency,
			MaxIdleConnsPerHost: concurrency,
			IdleConnTimeout:     30 * time.Second,
		},
	}

	var (
		completedRequests int64
		errorCount        int64
		wg                sync.WaitGroup
		requestChan       = make(chan Event, concurrency*2)
	)

	start := time.Now()

	for i := 0; i < concurrency; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for event := range requestChan {
				if err := sendEvent(client, serverURL, event); err != nil {
					atomic.AddInt64(&errorCount, 1)
					log.Printf("Worker %d error: %v", workerID, err)
				}
				
				completed := atomic.AddInt64(&completedRequests, 1)
				if completed%100000 == 0 {
					elapsed := time.Since(start)
					rate := float64(completed) / elapsed.Seconds()
					fmt.Printf("Completed: %d/%d (%.1f req/s)\n", completed, totalRequests, rate)
				}
			}
		}(i)
	}

	go func() {
		for i := 0; i < totalRequests; i++ {
			event := Event{
				UserID: fmt.Sprintf("user_%d", i%75000),
				Value:  float64(i%1000) + 0.5,
			}
			requestChan <- event
		}
		close(requestChan)
	}()

	wg.Wait()
	elapsed := time.Since(start)

	fmt.Printf("\n=== Test Results ===\n")
	fmt.Printf("Total time: %v\n", elapsed)
	fmt.Printf("Requests per second: %.2f\n", float64(totalRequests)/elapsed.Seconds())
	fmt.Printf("Errors: %d\n", errorCount)

	log.Printf("Test completed - Time: %v, RPS: %.2f, Errors: %d", elapsed, float64(totalRequests)/elapsed.Seconds(), errorCount)

	time.Sleep(100 * time.Millisecond)

	stats, err := getStats(client, serverURL)
	if err != nil {
		fmt.Printf("Error getting stats: %v\n", err)
		log.Printf("Error getting stats: %v", err)
		return
	}

	fmt.Printf("\n=== Server Stats ===\n")
	fmt.Printf("Total Requests: %d\n", stats.TotalRequests)
	fmt.Printf("Unique Users: %d\n", stats.UniqueUsers)
	fmt.Printf("Sum: %.2f\n", stats.Sum)
	fmt.Printf("Average: %.2f\n", stats.Avg)

	log.Printf("Server stats - Total: %d, Users: %d, Sum: %.2f, Avg: %.2f", 
		stats.TotalRequests, stats.UniqueUsers, stats.Sum, stats.Avg)

	if stats.TotalRequests == totalRequests {
		fmt.Println("\n✅ SUCCESS: All requests processed correctly!")
		log.Printf("SUCCESS: All %d requests processed correctly!", totalRequests)
	} else {
		fmt.Printf("\n❌ FAILED: Expected %d requests, got %d\n", totalRequests, stats.TotalRequests)
		log.Printf("FAILED: Expected %d requests, got %d", totalRequests, stats.TotalRequests)
	}
}

func sendEvent(client *http.Client, serverURL string, event Event) error {
	data, err := json.Marshal(event)
	if err != nil {
		return err
	}

	resp, err := client.Post(serverURL+"/event", "application/json", bytes.NewBuffer(data))
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
	}

	return nil
}

func getStats(client *http.Client, serverURL string) (*Stats, error) {
	resp, err := client.Get(serverURL + "/stats")
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	var stats Stats
	if err := json.NewDecoder(resp.Body).Decode(&stats); err != nil {
		return nil, err
	}

	return &stats, nil
}