use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use dashmap::DashSet;
use serde::{Deserialize, Serialize};
use warp::Filter;

#[derive(Debug, Deserialize)]
struct Event {
    #[serde(rename = "userId")]
    user_id: String,
    value: f64,
}

#[derive(Debug, Serialize)]
struct Stats {
    #[serde(rename = "totalRequests")]
    total_requests: u64,
    #[serde(rename = "uniqueUsers")]
    unique_users: usize,
    sum: f64,
    avg: f64,
}

#[derive(Clone)]
struct EventStorage {
    total_requests: Arc<AtomicU64>,
    sum: Arc<AtomicU64>, // Store as integer (multiply by 1000000 for precision)
    users: Arc<DashSet<String>>,
}

impl EventStorage {
    fn new() -> Self {
        Self {
            total_requests: Arc::new(AtomicU64::new(0)),
            sum: Arc::new(AtomicU64::new(0)),
            users: Arc::new(DashSet::new()),
        }
    }

    fn add_event(&self, event: Event) {
        // Increment total requests atomically
        self.total_requests.fetch_add(1, Ordering::Relaxed);
        
        // Add to sum (multiply by 1000000 for precision)
        let value_scaled = (event.value * 1_000_000.0) as u64;
        self.sum.fetch_add(value_scaled, Ordering::Relaxed);
        
        // Add user to set (DashSet handles concurrency)
        self.users.insert(event.user_id);
    }

    fn get_stats(&self) -> Stats {
        let total = self.total_requests.load(Ordering::Relaxed);
        let sum_scaled = self.sum.load(Ordering::Relaxed);
        let sum_actual = sum_scaled as f64 / 1_000_000.0;
        let unique_users = self.users.len();
        let avg = if total > 0 { sum_actual / total as f64 } else { 0.0 };

        Stats {
            total_requests: total,
            unique_users,
            sum: sum_actual,
            avg,
        }
    }
}

async fn handle_event(
    event: Event,
    storage: Arc<EventStorage>,
) -> Result<impl warp::Reply, warp::Rejection> {
    storage.add_event(event);
    Ok(warp::reply::with_status("OK", warp::http::StatusCode::OK))
}

async fn handle_stats(storage: Arc<EventStorage>) -> Result<impl warp::Reply, warp::Rejection> {
    let stats = storage.get_stats();
    Ok(warp::reply::json(&stats))
}

#[tokio::main]
async fn main() {
    let storage = Arc::new(EventStorage::new());

    let storage_for_events = storage.clone();
    let storage_for_stats = storage.clone();

    let event_route = warp::path("event")
        .and(warp::post())
        .and(warp::body::json())
        .and(warp::any().map(move || storage_for_events.clone()))
        .and_then(handle_event);

    let stats_route = warp::path("stats")
        .and(warp::get())
        .and(warp::any().map(move || storage_for_stats.clone()))
        .and_then(handle_stats);

    let routes = event_route.or(stats_route);

    println!("Starting server on http://localhost:8080");
    
    warp::serve(routes)
        .run(([0, 0, 0, 0], 8080))
        .await;
}