import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const eventsSent = new Counter('events_sent');
const errorRate = new Rate('error_rate');
const requestDuration = new Trend('request_duration_ms');

// Configuration
const TOTAL_REQUESTS = parseInt(__ENV.TOTAL_REQUESTS || '1000000');
const TARGET_RPS = parseInt(__ENV.TARGET_RPS || '10000'); // Default 10k RPS (adjustable)
const USER_POOL_SIZE = 75000;

export const options = {
  scenarios: {
    one_million_requests: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,              // Requests per timeUnit
      timeUnit: '1s',                // Per second
      duration: `${Math.ceil(TOTAL_REQUESTS / TARGET_RPS)}s`, // Auto-calculate duration
      preAllocatedVUs: 500,          // Pre-allocated virtual users
      maxVUs: 2000,                  // Maximum virtual users if needed
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // Response time thresholds
    error_rate: ['rate<0.01'],                       // Less than 1% errors
    http_req_failed: ['rate<0.01'],                  // Less than 1% failed requests
  },

  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Get target URL from environment variable
const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

function getUserId(requestNum) {
  return `user_${requestNum % USER_POOL_SIZE}`;
}

export default function () {
  // Generate unique request identifier
  const requestId = __VU * 100000 + __ITER;

  const payload = JSON.stringify({
    userId: getUserId(requestId),
    value: (requestId % 1000) + 0.5,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '10s',
  };

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/event`, payload, params);
  const duration = Date.now() - startTime;

  // Validate response
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  // Record metrics
  eventsSent.add(1);
  errorRate.add(!success);
  requestDuration.add(duration);

  if (!success) {
    console.error(`Request failed: ${response.status} - ${response.body}`);
  }
}

export function setup() {
  console.log('\n╔═══════════════════════════════════════════════════════════════╗');
  console.log('║        One Million Request Challenge - K6 Load Test          ║');
  console.log('╠═══════════════════════════════════════════════════════════════╣');
  console.log(`║  Target URL:          ${BASE_URL.padEnd(39, ' ')}║`);
  console.log(`║  Total Requests:      ${TOTAL_REQUESTS.toLocaleString().padEnd(39, ' ')}║`);
  console.log(`║  Target RPS:          ${TARGET_RPS.toLocaleString().padEnd(39, ' ')}║`);
  console.log(`║  Expected Duration:   ~${Math.ceil(TOTAL_REQUESTS / TARGET_RPS)}s`.padEnd(64, ' ') + '║');
  console.log('╚═══════════════════════════════════════════════════════════════╝\n');

  // Health check - try /health first, fallback to /stats
  console.log('Performing health check...');
  let healthResponse = http.get(`${BASE_URL}/health`, { timeout: '30s' });

  if (healthResponse.status !== 200) {
    console.log('⚠  /health endpoint not available, trying /stats...');
    healthResponse = http.get(`${BASE_URL}/stats`, { timeout: '30s' });

    if (healthResponse.status !== 200) {
      throw new Error(`Server check failed: ${healthResponse.status} - ${healthResponse.body}`);
    }
  }

  console.log('✓ Server is responding\n');
  console.log(`Starting load test (${TARGET_RPS.toLocaleString()} req/s for ~${Math.ceil(TOTAL_REQUESTS / TARGET_RPS)}s)...\n`);

  return { startTime: new Date() };
}

export function teardown(data) {
  const endTime = new Date();
  const totalDuration = (endTime - data.startTime) / 1000;

  console.log('\n╔═══════════════════════════════════════════════════════════════╗');
  console.log('║                     Load Test Complete                       ║');
  console.log('╚═══════════════════════════════════════════════════════════════╝\n');

  // Wait a moment for server to process remaining requests
  sleep(2);

  // Fetch final statistics from server
  console.log('Fetching server statistics...\n');
  const statsResponse = http.get(`${BASE_URL}/stats`);

  if (statsResponse.status === 200) {
    try {
      const stats = JSON.parse(statsResponse.body);

      console.log('╔═══════════════════════════════════════════════════════════════╗');
      console.log('║                    Server Statistics                          ║');
      console.log('╠═══════════════════════════════════════════════════════════════╣');
      console.log(`║  Total Requests:      ${stats.totalRequests.toLocaleString().padStart(15, ' ')}                    ║`);
      console.log(`║  Unique Users:        ${stats.uniqueUsers.toLocaleString().padStart(15, ' ')}                    ║`);
      console.log(`║  Sum:                 ${stats.sum.toFixed(2).padStart(15, ' ')}                    ║`);
      console.log(`║  Average:             ${stats.avg.toFixed(4).padStart(15, ' ')}                    ║`);
      console.log('╠═══════════════════════════════════════════════════════════════╣');
      console.log(`║  Test Duration:       ${totalDuration.toFixed(2)}s`.padEnd(64, ' ') + '║');
      console.log(`║  Actual RPS:          ${(stats.totalRequests / totalDuration).toFixed(2)}`.padEnd(64, ' ') + '║');
      console.log('╚═══════════════════════════════════════════════════════════════╝\n');

      // Validation
      if (stats.totalRequests >= TOTAL_REQUESTS * 0.99) {
        console.log('✅ SUCCESS: 1 million requests processed!\n');
      } else {
        console.log(`⚠️  WARNING: Expected ${TOTAL_REQUESTS.toLocaleString()}, got ${stats.totalRequests.toLocaleString()}\n`);
      }

      // Verify aggregation correctness
      const expectedAvg = stats.sum / stats.totalRequests;
      if (Math.abs(expectedAvg - stats.avg) < 0.01) {
        console.log('✅ Aggregation is correct\n');
      } else {
        console.log('❌ Aggregation error detected\n');
      }

    } catch (e) {
      console.error('Failed to parse server statistics:', e);
    }
  } else {
    console.error(`Failed to fetch stats: ${statsResponse.status}`);
  }
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'loadtest-results.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options = {}) {
  const indent = options.indent || '';
  const colors = options.enableColors !== false;

  let output = '\n';
  output += indent + '═'.repeat(65) + '\n';
  output += indent + '              K6 Load Test Summary\n';
  output += indent + '═'.repeat(65) + '\n\n';

  if (data.metrics.iterations) {
    output += indent + `Total Requests:     ${data.metrics.iterations.values.count}\n`;
  }

  if (data.metrics.http_req_duration) {
    const dur = data.metrics.http_req_duration.values;
    output += indent + `Request Duration:\n`;
    output += indent + `  min:   ${dur.min.toFixed(2)}ms\n`;
    output += indent + `  avg:   ${dur.avg.toFixed(2)}ms\n`;
    output += indent + `  med:   ${dur.med.toFixed(2)}ms\n`;
    output += indent + `  p(90): ${dur['p(90)'].toFixed(2)}ms\n`;
    output += indent + `  p(95): ${dur['p(95)'].toFixed(2)}ms\n`;
    output += indent + `  p(99): ${dur['p(99)'].toFixed(2)}ms\n`;
    output += indent + `  max:   ${dur.max.toFixed(2)}ms\n`;
  }

  if (data.metrics.http_req_failed) {
    const failRate = data.metrics.http_req_failed.values.rate * 100;
    output += indent + `\nFailed Requests:    ${failRate.toFixed(2)}%\n`;
  }

  output += indent + '\n' + '═'.repeat(65) + '\n';

  return output;
}