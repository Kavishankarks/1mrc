use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use serde::{Deserialize, Serialize};
use tokio::time::sleep;

#[derive(Debug, Serialize)]
struct Event {
    #[serde(rename = "userId")]
    user_id: String,
    value: f64,
}

#[derive(Debug, Deserialize)]
struct Stats {
    #[serde(rename = "totalRequests")]
    total_requests: u64,
    #[serde(rename = "uniqueUsers")]
    unique_users: usize,
    sum: f64,
    avg: f64,
}

#[derive(Clone)]
struct LoadTestClient {
    base_url: String,
    total_requests: Arc<AtomicU64>,
    successful_requests: Arc<AtomicU64>,
    failed_requests: Arc<AtomicU64>,
}

impl LoadTestClient {
    fn new(base_url: &str) -> Self {
        // Configure surf for HTTP/2 and better connection handling
        surf::Config::new()
            .set_max_connections_per_host(100)
            .set_timeout(Some(Duration::from_secs(10)))
            .try_into()
            .expect("Failed to configure surf");

        Self {
            base_url: base_url.to_string(),
            total_requests: Arc::new(AtomicU64::new(0)),
            successful_requests: Arc::new(AtomicU64::new(0)),
            failed_requests: Arc::new(AtomicU64::new(0)),
        }
    }

    fn generate_event(&self, user_id: u64) -> Event {
        Event {
            user_id: format!("user_{}", user_id),
            value: fastrand::f64() * 1000.0,
        }
    }

    async fn send_event(&self, event: Event) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        self.total_requests.fetch_add(1, Ordering::Relaxed);
        
        let mut response = surf::post(&format!("{}/event", self.base_url))
            .body_json(&event)?
            .send()
            .await?;

        if response.status().is_success() {
            self.successful_requests.fetch_add(1, Ordering::Relaxed);
        } else {
            self.failed_requests.fetch_add(1, Ordering::Relaxed);
        }

        Ok(())
    }

    async fn get_stats(&self) -> Result<Stats, Box<dyn std::error::Error + Send + Sync>> {
        let mut response = surf::get(&format!("{}/stats", self.base_url))
            .send()
            .await?;

        let stats: Stats = response.body_json().await?;
        Ok(stats)
    }

    async fn run_batch(&self, batch_size: usize, user_offset: u64) {
        let mut tasks = Vec::with_capacity(batch_size);
        
        for i in 0..batch_size {
            let event = self.generate_event(user_offset + i as u64);
            let client = self.clone();
            
            let task = tokio::spawn(async move {
                if let Err(e) = client.send_event(event).await {
                    eprintln!("Request failed: {}", e);
                    client.failed_requests.fetch_add(1, Ordering::Relaxed);
                }
            });
            
            tasks.push(task);
        }

        // Wait for all requests in this batch to complete
        for task in tasks {
            let _ = task.await;
        }
    }

    async fn run_load_test(&self, total_requests: usize, batch_size: usize, max_concurrent_batches: usize) {
        println!("🚀 Starting Surf HTTP/2 load test");
        println!("📊 Target: {} requests", total_requests);
        println!("📦 Batch size: {}", batch_size);
        println!("🔄 Max concurrent batches: {}", max_concurrent_batches);
        
        // Test connectivity
        print!("🔌 Testing connectivity... ");
        match self.get_stats().await {
            Ok(stats) => println!("✅ Connected! Initial stats: {:?}", stats),
            Err(e) => {
                println!("❌ Connection failed: {}", e);
                return;
            }
        }

        let start_time = Instant::now();
        let num_batches = (total_requests + batch_size - 1) / batch_size;
        let semaphore = Arc::new(tokio::sync::Semaphore::new(max_concurrent_batches));
        
        let mut batch_tasks = Vec::new();
        
        for batch_idx in 0..num_batches {
            let client = self.clone();
            let semaphore = semaphore.clone();
            let user_offset = (batch_idx * batch_size) as u64;
            let current_batch_size = std::cmp::min(batch_size, total_requests - batch_idx * batch_size);
            
            let task = tokio::spawn(async move {
                let _permit = semaphore.acquire().await.unwrap();
                client.run_batch(current_batch_size, user_offset).await;
            });
            
            batch_tasks.push(task);
            
            // Progress reporting
            if (batch_idx + 1) % 100 == 0 {
                println!("📈 Queued {}/{} batches", batch_idx + 1, num_batches);
            }
        }

        println!("⚡ Executing all requests...");
        
        // Wait for all batches to complete
        for task in batch_tasks {
            let _ = task.await;
        }

        let duration = start_time.elapsed();
        
        // Wait a moment for the server to process final requests
        sleep(Duration::from_millis(100)).await;
        
        // Get final statistics
        println!("📋 Fetching final statistics...");
        let final_stats = self.get_stats().await.unwrap_or_else(|e| {
            eprintln!("Failed to get final stats: {}", e);
            Stats {
                total_requests: 0,
                unique_users: 0,
                sum: 0.0,
                avg: 0.0,
            }
        });

        self.print_results(duration, &final_stats);
    }

    fn print_results(&self, duration: Duration, server_stats: &Stats) {
        let total = self.total_requests.load(Ordering::Relaxed);
        let successful = self.successful_requests.load(Ordering::Relaxed);
        let failed = self.failed_requests.load(Ordering::Relaxed);
        let duration_secs = duration.as_secs_f64();

        println!("\n{}", "=".repeat(60));
        println!("🎯 SURF HTTP/2 LOAD TEST RESULTS");
        println!("{}", "=".repeat(60));
        println!("⏱️  Total time: {:.2} seconds", duration_secs);
        println!("📤 Client stats:");
        println!("   • Requests sent: {}", total);
        println!("   • Successful: {} ({:.1}%)", successful, (successful as f64 / total as f64) * 100.0);
        println!("   • Failed: {} ({:.1}%)", failed, (failed as f64 / total as f64) * 100.0);
        println!("🚄 Performance:");
        println!("   • Requests/second: {:.2}", total as f64 / duration_secs);
        println!("   • Avg latency: {:.2}ms", (duration_secs * 1000.0) / total as f64);
        println!("\n📊 Server statistics:");
        println!("   • Total requests: {}", server_stats.total_requests);
        println!("   • Unique users: {}", server_stats.unique_users);
        println!("   • Sum: {:.2}", server_stats.sum);
        println!("   • Average: {:.6}", server_stats.avg);
        
        if server_stats.total_requests != total {
            println!("⚠️  Warning: Client sent {} but server received {}", 
                     total, server_stats.total_requests);
        } else {
            println!("✅ All requests successfully processed!");
        }
    }
}

#[tokio::main]
async fn main() {
    let base_url = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "http://127.0.0.1:8080".to_string());
    
    let client = LoadTestClient::new(&base_url);
    
    // Warm-up test
    println!("🔥 Running warm-up test (10,000 requests)...");
    client.run_load_test(10_000, 100, 50).await;
    
    // Reset counters
    client.total_requests.store(0, Ordering::Relaxed);
    client.successful_requests.store(0, Ordering::Relaxed);
    client.failed_requests.store(0, Ordering::Relaxed);
    
    println!("\n{}", "=".repeat(60));
    println!("🎯 MAIN CHALLENGE: 1,000,000 REQUESTS");
    println!("{}", "=".repeat(60));
    
    // Main load test with HTTP/2 optimizations
    client.run_load_test(1_000_000, 1000, 100).await;
}