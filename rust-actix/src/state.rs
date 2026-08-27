use crate::models;
use crate::models::{Event, Message};
use std::collections::HashSet;
use std::sync::mpsc::{Receiver, Sender};
use std::thread;

#[derive(Clone)]
pub struct AppState {
    tx: Sender<Message>,
}

impl AppState {
    pub fn new(tx: Sender<Message>) -> AppState {
        AppState { tx }
    }

    pub fn event(&self, event: Event) {
        self.tx.send(Message::Event(event)).unwrap();
    }

    pub fn stats(&self, tx: tokio::sync::oneshot::Sender<models::Stats>) {
        self.tx.send(Message::Stats(tx)).unwrap();
    }
}

struct Stats {
    total_requests: u64,
    sum: f64,
    users: HashSet<String>,
}

pub fn start_event_handler(rx: Receiver<Message>) {
    thread::spawn(move || {
        let mut stats = Stats {
            total_requests: 0,
            sum: 0.0,
            users: HashSet::new(),
        };
        for m in rx {
            match m {
                Message::Event(event) => {
                    stats.total_requests += 1;
                    stats.sum = stats.sum + event.value;
                    stats.users.insert(event.user_id);
                }
                Message::Stats(stats_sender) => {
                    let avg = if stats.total_requests == 0 {
                        0.0
                    } else {
                        stats.sum / stats.total_requests as f64
                    };
                    let stats: models::Stats = models::Stats::new(
                        stats.total_requests,
                        stats.users.len(),
                        stats.sum,
                        avg
                    );
                    stats_sender.send(stats).unwrap();
                }
            };
        }
        println!("Event Handler completed")
    });
}
