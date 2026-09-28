import http from 'k6/http';
import { check } from 'k6';

const base = __ENV.BASE_URL || 'http://localhost:8080';
const email = __ENV.EMAIL;
const password = __ENV.PASSWORD;
if (!email || !password) throw new Error('Set EMAIL and PASSWORD for an existing user');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.FLUXGUARD_RATE || 5),
      timeUnit: '1s',
      duration: __ENV.FLUXGUARD_DURATION || '1m',
      preAllocatedVUs: 10,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const login = http.post(`${base}/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (login.status !== 200) throw new Error(`Login failed: ${login.status}`);
  return { token: login.json('accessToken') };
}

export default function ({ token }) {
  const response = http.get(`${base}/api/products`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(response, { 'products available': r => r.status === 200 });
}
