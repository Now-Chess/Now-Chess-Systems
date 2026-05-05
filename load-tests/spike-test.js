import http from 'k6/http';
import { check, sleep } from 'k6';
import { ENDPOINTS } from './config.js';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '30s', target: 500, ramp: 'fast' },
    { duration: '2m', target: 500 },
    { duration: '30s', target: 50, ramp: 'fast' },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.3'],
  },
};

export default function () {
  // Rapid account checks
  const urls = [
    ENDPOINTS.accountPublicProfile('testuser'),
    ENDPOINTS.storeGame,
    ENDPOINTS.accountProfile,
  ];

  urls.forEach((url) => {
    const res = http.get(url);
    check(res, {
      'response received': (r) => r.status > 0,
      'response time under 3s': (r) => r.timings.duration < 3000,
    });
  });

  sleep(0.1);
}
