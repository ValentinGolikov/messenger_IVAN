/**
 * stress.js — стресс-тест: постепенно увеличиваем нагрузку до отказа.
 *
 * Цель: найти точку, при которой сервис начинает деградировать
 * (latency растёт, ошибки появляются).
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 stress.js
 *
 * Смотри на:
 *  - http_req_duration p(95) — когда начинает расти
 *  - http_req_failed rate — когда появляются ошибки
 *  - iteration_duration — общее время итерации
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, TEST_USER_IDS, TEST_GROUP_CHAT_ID } from './config.js';

export const options = {
  stages: [
    { duration: '1m',  target: 10  },  // warm up
    { duration: '2m',  target: 50  },  // normal load
    { duration: '2m',  target: 100 },  // moderate stress
    { duration: '2m',  target: 200 },  // high stress
    { duration: '2m',  target: 300 },  // very high stress
    { duration: '1m',  target: 0   },  // recovery
  ],
  thresholds: {
    // These are soft thresholds — test continues even if breached
    http_req_failed:   [{ threshold: 'rate<0.10', abortOnFail: false }],
    http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: false }],
  },
};

export default function () {
  const userId = TEST_USER_IDS[__VU % TEST_USER_IDS.length];

  // Mix of read-heavy operations (realistic for a messenger)
  const scenario = Math.random();

  if (scenario < 0.4) {
    // 40% — load chat list (most common action)
    const res = http.get(`${BASE_URL}/chats/${userId}`);
    check(res, { 'chat list ok': (r) => r.status === 200 });

  } else if (scenario < 0.7) {
    // 30% — load messages
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`);
    check(res, { 'messages ok': (r) => r.status === 200 });

  } else if (scenario < 0.85) {
    // 15% — search
    const q = String.fromCharCode(97 + Math.floor(Math.random() * 26)); // random letter
    const res = http.get(`${BASE_URL}/users/search?q=${q}&selfId=${userId}`);
    check(res, { 'search ok': (r) => r.status === 200 });

  } else if (scenario < 0.95) {
    // 10% — presence check
    const ids = TEST_USER_IDS.slice(0, 10).join(',');
    const res = http.get(`${BASE_URL}/users/presence?ids=${ids}`);
    check(res, { 'presence ok': (r) => r.status === 200 });

  } else {
    // 5% — global search (expensive: hits Cassandra + Postgres)
    const res = http.get(`${BASE_URL}/search/global?q=test&userId=${userId}`);
    check(res, { 'global search ok': (r) => r.status === 200 });
  }

  sleep(0.1); // minimal think time to maximize pressure
}
