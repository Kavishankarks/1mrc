package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
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

type EventStore struct {
	totalRequests int64
	sum           uint64
	users         sync.Map
	userCount     int64
}

func NewEventStore() *EventStore {
	return &EventStore{
		users: sync.Map{},
	}
}

func (es *EventStore) AddEvent(event Event) {
	atomic.AddInt64(&es.totalRequests, 1)

	sumBits := atomic.LoadUint64(&es.sum)
	for {
		newSum := float64FromBits(sumBits) + event.Value
		newSumBits := float64ToBits(newSum)
		if atomic.CompareAndSwapUint64(&es.sum, sumBits, newSumBits) {
			break
		}
		sumBits = atomic.LoadUint64(&es.sum)
	}

	if _, exists := es.users.LoadOrStore(event.UserID, struct{}{}); !exists {
		atomic.AddInt64(&es.userCount, 1)
	}
}

func (es *EventStore) GetStats() Stats {
	totalReqs := atomic.LoadInt64(&es.totalRequests)
	sumValue := float64FromBits(atomic.LoadUint64(&es.sum))
	uniqueUsers := atomic.LoadInt64(&es.userCount)

	var avg float64
	if totalReqs > 0 {
		avg = sumValue / float64(totalReqs)
	}

	return Stats{
		TotalRequests: totalReqs,
		UniqueUsers:   uniqueUsers,
		Sum:           sumValue,
		Avg:           avg,
	}
}

func float64ToBits(f float64) uint64 {
	return math.Float64bits(f)
}

func float64FromBits(b uint64) float64 {
	return math.Float64frombits(b)
}

var store *EventStore

func main() {
	store = NewEventStore()

	http.HandleFunc("/event", handleEvent)
	http.HandleFunc("/stats", handleStats)
	http.HandleFunc("/health", handleHealth)

	server := &http.Server{
		Addr:           ":8080",
		ReadTimeout:    10 * time.Second,
		WriteTimeout:   10 * time.Second,
		MaxHeaderBytes: 1 << 20,
	}

	fmt.Println("Server starting on :8080")
	log.Fatal(server.ListenAndServe())
}

func handleEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var event Event
	if err := json.NewDecoder(r.Body).Decode(&event); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	if event.UserID == "" {
		http.Error(w, "userId is required", http.StatusBadRequest)
		return
	}

	store.AddEvent(event)

	w.WriteHeader(http.StatusOK)
}


func handleStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	stats := store.GetStats()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "healthy"})
}