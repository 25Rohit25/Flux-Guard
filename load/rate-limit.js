import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const base = __ENV.BASE_URL || 'http://localhost:8080';
const email = __ENV.EMAIL;
const password = __ENV.PASSWORD;
if (!email || !password) throw new Error('Set EMAIL and PASSWORD for an existing user');
const limited = new Counter('rate_limited');

export const options = {
  vus: 10,
  iterations: 500,
  thresholds: { rate_limited: ['count>0'] },
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
  if (response.status === 429) limited.add(1);
  check(response, { 'allowed or limited': r => r.status === 200 || r.status === 429 });
}
