import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

export const transferSuccess = new Rate('transfer_success');
export const transferDuration = new Trend('transfer_duration');

const REQUESTS_PER_MINUTE = 1_000_000;
const RATE_PER_SECOND = Math.floor(REQUESTS_PER_MINUTE / 60);

export const options = {
  discardResponseBodies: true,
  scenarios: {
    million_rpm: {
      executor: 'constant-arrival-rate',
      rate: RATE_PER_SECOND,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 2000,
      maxVUs: 10000,
    },
    warmup: {
      executor: 'ramping-arrival-rate',
      startRate: 1000,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      stages: [
        { target: Math.floor(RATE_PER_SECOND / 4), duration: '2m' },
        { target: Math.floor(RATE_PER_SECOND / 2), duration: '2m' },
      ],
      exec: 'warmupScenario',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<15', 'p(99)<30'],
    transfer_success: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCOUNTS = __ENV.ACCOUNTS || 250000;
const CURRENCY = __ENV.CURRENCY || 'BRL';

export default function () {
  sendTransfer();
}

export function warmupScenario () {
  sendTransfer();
}

function sendTransfer () {
  const from = Math.floor(Math.random() * ACCOUNTS);
  const to = (from + 1) % ACCOUNTS;
  const payload = JSON.stringify({
    idempotencyKey: `${__VU}-${__ITER}-${Date.now()}`,
    fromAccount: `ACC-${from.toString().padStart(8, '0')}`,
    toAccount: `ACC-${to.toString().padStart(8, '0')}`,
    amountInCents: 2500,
    currency: CURRENCY,
  });

  const res = http.post(`${BASE_URL}/api/transfers`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '5s',
  });

  transferSuccess.add(res.status === 202);
  transferDuration.add(res.timings.duration);
  check(res, {
    'aceitou a transferencia': r => r.status === 202,
  });
}
