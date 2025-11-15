import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

export const statsDuration = new Trend('stats_request_duration');

const REQUESTS_PER_MINUTE = Number(__ENV.REQUESTS_PER_MINUTE || 60_000);
const REQUESTS_PER_SECOND = Number(__ENV.REQUESTS_PER_SECOND || 0);
const RATE_PER_SECOND = Math.floor(
  REQUESTS_PER_SECOND > 0 ? REQUESTS_PER_SECOND : REQUESTS_PER_MINUTE / 60,
);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    stats_rpm: {
      executor: 'constant-arrival-rate',
      rate: RATE_PER_SECOND,
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<100', 'p(99)<200'],
  },
  summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/api/transfers/stats`, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '5s',
  });

  statsDuration.add(res.timings.duration);
  check(res, {
    'stats respondeu 200': r => r.status === 200,
  });
}
