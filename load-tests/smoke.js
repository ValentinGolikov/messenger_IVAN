/**
 * smoke.js — быстрая проверка что сервис живой и отвечает корректно.
 * 1 виртуальный пользователь, 30 секунд.
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 smoke.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEST_USER_IDS, TEST_GROUP_CHAT_ID } from './config.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const userId = TEST_USER_IDS[0];

  // GET /chats/{userId}
  let res = http.get(`${BASE_URL}/chats/${userId}`);
  check(res, {
    'GET /chats status 200': (r) => r.status === 200,
    'GET /chats returns array': (r) => {
      try { return Array.isArray(JSON.parse(r.body)); } catch { return false; }
    },
  });

  sleep(0.5);

  // GET /chats/{chatId}/messages
  res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`);
  check(res, {
    'GET /messages status 200': (r) => r.status === 200,
  });

  sleep(0.5);

  // GET /users/search
  res = http.get(`${BASE_URL}/users/search?q=Test&selfId=${userId}`);
  check(res, {
    'GET /users/search status 200': (r) => r.status === 200,
  });

  sleep(0.5);

  // GET /users/presence
  const ids = TEST_USER_IDS.slice(0, 5).join(',');
  res = http.get(`${BASE_URL}/users/presence?ids=${ids}`);
  check(res, {
    'GET /users/presence status 200': (r) => r.status === 200,
  });

  sleep(1);
}
