import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Metrics
const eventsSent = new Counter('events_sent');
const errorRate = new Rate('error_rate');

// Spike test - sudden burst of traffic
export const options = {
  stages: [
    { duration: '30s', target: 100 },     // Normal load
    { duration: '10s', target: 5000 },    // SPIKE! 50x increase
    { duration: '30s', target: 5000 },    // Maintain spike
    { duration: '10s', target: 100 },     // Back to normal
    { duration: '30s', target: 100 },     // Recovery
    { duration: '10s', target: 0 },       // Ramp down
  ],

  thresholds: {
    http_req_duration: ['p(99)<5000'], // Allow higher latency during spike
    error_rate: ['rate<0.10'],         // Allow up to 10% errors during spike
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

  const response = http.post(`${BASE_URL}/event`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  });

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  eventsSent.add(1);
  errorRate.add(!success);
}

export function setup() {
  console.log('\n=== Spike Test Starting ===');
  console.log(`Target: ${BASE_URL}`);
  console.log('This test will simulate sudden traffic spikes\n');

  const health = http.get(`${BASE_URL}/health`);
  if (health.status !== 200) {
    throw new Error('Health check failed');
  }

  return { startTime: new Date() };
}

export function teardown(data) {
  console.log('\n=== Spike Test Complete ===');
  console.log(`Duration: ${((new Date() - data.startTime) / 1000).toFixed(2)}s`);

  const stats = http.get(`${BASE_URL}/stats`);
  if (stats.status === 200) {
    console.log('\n=== Server Stats ===');
    console.log(stats.body);
  }
}