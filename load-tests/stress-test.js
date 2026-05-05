import http from 'k6/http';
import { check, sleep } from 'k6';
import { ENDPOINTS } from './config.js';

export const options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '5m', target: 200 },
    { duration: '5m', target: 300 },
    { duration: '5m', target: 400 },
    { duration: '5m', target: 500 },
    { duration: '5m', target: 400 },
    { duration: '5m', target: 200 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.2'],
  },
};

export default function () {
  const userIndex = Math.floor(Math.random() * 10);

  // Burst account endpoint
  const accountRes = http.get(ENDPOINTS.accountPublicProfile(`user-${userIndex}`));
  check(accountRes, {
    'account endpoint responds': (r) => r.status > 0,
  });

  // Burst store endpoint
  const storeRes = http.get(ENDPOINTS.storeGame);
  check(storeRes, {
    'store endpoint responds': (r) => r.status > 0,
  });

  sleep(Math.random() * 0.5);
}
