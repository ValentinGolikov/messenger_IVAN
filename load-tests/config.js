/**
 * Shared configuration for all load test scripts.
 *
 * The server uses userId-based auth (no JWT/session tokens).
 * We pre-seed test users via seed.js and reuse their IDs here.
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
export const WS_URL   = BASE_URL.replace(/^http/, 'ws');

// How many virtual users / duration — override via --env flags
export const VU_COUNT = parseInt(__ENV.USER_COUNT || '50');
export const DURATION = __ENV.DURATION || '2m';

/**
 * Pre-seeded test user IDs.
 * Run seed.js once before tests to populate these users.
 * IDs are assigned by PostgreSQL autoincrement — seed.js prints them.
 *
 * If you re-seed, update these arrays.
 */
export const TEST_USER_IDS = Array.from({ length: 20 }, (_, i) => i + 1); // [1..20]

// A shared group chat ID created by seed.js
export const TEST_GROUP_CHAT_ID = 1;

// Thresholds used across tests
export const COMMON_THRESHOLDS = {
  http_req_failed:   ['rate<0.01'],          // <1% errors
  http_req_duration: ['p(95)<500'],          // 95% of requests under 500ms
};
