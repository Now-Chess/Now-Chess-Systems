export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const ACCOUNT_HOST = __ENV.ACCOUNT_HOST || 'http://localhost:8083';
export const STORE_HOST = __ENV.STORE_HOST || 'http://localhost:8085';
export const CORE_HOST = BASE_URL;

export const ENDPOINTS = {
  // Account endpoints
  accountCreateUser: `${ACCOUNT_HOST}/api/account`,
  accountLogin: `${ACCOUNT_HOST}/api/account/login`,
  accountProfile: `${ACCOUNT_HOST}/api/account/me`,
  accountPublicProfile: (username) => `${ACCOUNT_HOST}/api/account/${username}`,

  // Store endpoints
  storeGame: `${STORE_HOST}/api/games`,
  storeGameById: (gameId) => `${STORE_HOST}/api/games/${gameId}`,

  // Core endpoints (game operations)
  gameWebSocket: (gameId) => `ws://localhost:8080/api/games/${gameId}/ws`,
};

export const TEST_USERS = [
  { username: 'load-test-user-1', email: 'load1@example.com' },
  { username: 'load-test-user-2', email: 'load2@example.com' },
  { username: 'load-test-user-3', email: 'load3@example.com' },
];
