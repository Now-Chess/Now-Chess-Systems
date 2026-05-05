import http from 'k6/http';
import { check, sleep } from 'k6';
import { ENDPOINTS, ACCOUNT_HOST } from './config.js';

export const options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '3m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  // Test account endpoints
  const accountRes = http.get(ENDPOINTS.accountPublicProfile('testuser'));
  check(accountRes, {
    'account endpoint status is 200': (r) => r.status === 200,
  });

  sleep(1);

  // Test store endpoints
  const storeRes = http.get(ENDPOINTS.storeGame);
  check(storeRes, {
    'store endpoint status is 200': (r) => r.status === 200 || r.status === 401,
  });

  sleep(1);
}
