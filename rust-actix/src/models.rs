use serde::{Deserialize, Serialize};
use tokio::sync::oneshot::Sender;

#[derive(Deserialize, Debug)]
pub struct Event {
    #[serde(rename = "userId")]
    pub(crate) user_id: String,
    pub(crate) value: f64,
}

#[derive(Serialize, Debug)]
pub struct Stats {
    #[serde(rename = "totalRequests")]
    pub total_requests: u64,
    #[serde(rename = "uniqueUsers")]
    pub unique_users: usize,
    pub sum: f64,
    pub avg: f64,
}

impl Stats {

    pub fn default() -> Self {
        Self::new(0, 0, 0.0, 0.0)
    }

    pub fn new(total_requests: u64,
               unique_users: usize,
               sum: f64,
               avg: f64) -> Self {
        Stats {
            total_requests,
            unique_users,
            sum,
            avg
        }
    }
}

pub enum Message {
    Event(Event),
    Stats(Sender<Stats>),
}