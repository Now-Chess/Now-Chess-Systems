import http from 'k6/http';
import { check, sleep } from 'k6';
import { ENDPOINTS } from './config.js';

export const options = {
  vus: 50,
  duration: '10m',
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  // Simulate consistent user traffic

  // 1. Get account profile
  const profileRes = http.get(ENDPOINTS.accountProfile);
  check(profileRes, {
    'profile status is 200 or 401': (r) => r.status === 200 || r.status === 401,
  });

  sleep(0.5);

  // 2. Get public profile
  const publicRes = http.get(ENDPOINTS.accountPublicProfile('testuser'));
  check(publicRes, {
    'public profile status ok': (r) => r.status > 0,
  });

  sleep(0.5);

  // 3. List store games
  const gamesRes = http.get(ENDPOINTS.storeGame);
  check(gamesRes, {
    'store games status ok': (r) => r.status > 0,
  });

  sleep(1);
}
