import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const eventsSent = new Counter('events_sent');
const errorRate = new Rate('error_rate');

// Stress test configuration - gradually increase load to find breaking point
export const options = {
  stages: [
    { duration: '30s', target: 100 },     // Ramp up to 100 VUs
    { duration: '1m', target: 500 },      // Ramp up to 500 VUs
    { duration: '1m', target: 1000 },     // Ramp up to 1000 VUs
    { duration: '2m', target: 2000 },     // Ramp up to 2000 VUs (stress)
    { duration: '2m', target: 3000 },     // Ramp up to 3000 VUs (breaking point?)
    { duration: '1m', target: 5000 },     // Spike to 5000 VUs
    { duration: '2m', target: 1000 },     // Scale back down
    { duration: '1m', target: 0 },        // Ramp down to 0
  ],

  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    error_rate: ['rate<0.05'], // Allow up to 5% errors during stress test
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';
const USER_POOL_SIZE = 75000;

function getUserId(requestNum) {
  return `user_${requestNum % USER_POOL_SIZE}`;
}

export default function () {
  const requestId = __VU * 100000 + __ITER;

  const payload = JSON.stringify({
    userId: getUserId(requestId),
    value: (requestId % 1000) + 0.5,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
    timeout: '10s',
  };

  const response = http.post(`${BASE_URL}/event`, payload, params);

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  eventsSent.add(1);
  errorRate.add(!success);

  // Small sleep to simulate think time
  sleep(0.1);
}

export function setup() {
  console.log('\n=== Stress Test Starting ===');
  console.log(`Target: ${BASE_URL}`);
  console.log('This test will gradually increase load to find the breaking point\n');

  const health = http.get(`${BASE_URL}/health`);
  if (health.status !== 200) {
    throw new Error(`Health check failed: ${health.status}`);
  }

  return { startTime: new Date() };
}

export function teardown(data) {
  const duration = (new Date() - data.startTime) / 1000;

  console.log('\n=== Stress Test Complete ===');
  console.log(`Duration: ${duration.toFixed(2)}s`);

  sleep(2);

  const stats = http.get(`${BASE_URL}/stats`);
  if (stats.status === 200) {
    console.log('\n=== Server Stats ===');
    console.log(stats.body);
  }
}