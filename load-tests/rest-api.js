/**
 * rest-api.js — нагрузочный тест HTTP REST эндпоинтов.
 *
 * Сценарий имитирует типичное поведение пользователя:
 *  - открыть список чатов
 *  - открыть чат и загрузить сообщения
 *  - поискать пользователей
 *  - проверить онлайн-статус
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 rest-api.js
 *   k6 run --env BASE_URL=http://localhost:8081 --env USER_COUNT=100 --env DURATION=5m rest-api.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, VU_COUNT, DURATION, TEST_USER_IDS, TEST_GROUP_CHAT_ID, COMMON_THRESHOLDS } from './config.js';

// Custom metrics
const chatListDuration  = new Trend('chat_list_duration');
const messagesDuration  = new Trend('messages_duration');
const searchDuration    = new Trend('search_duration');
const errorRate         = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '30s', target: Math.floor(VU_COUNT * 0.3) }, // ramp up to 30%
    { duration: '1m',  target: VU_COUNT },                   // ramp up to full load
    { duration: DURATION, target: VU_COUNT },                 // hold
    { duration: '30s', target: 0 },                          // ramp down
  ],
  thresholds: {
    ...COMMON_THRESHOLDS,
    chat_list_duration:  ['p(95)<300'],
    messages_duration:   ['p(95)<400'],
    search_duration:     ['p(95)<500'],
  },
};

export default function () {
  // Each VU picks a random test user
  const userId = TEST_USER_IDS[__VU % TEST_USER_IDS.length];

  group('Chat list', () => {
    const res = http.get(`${BASE_URL}/chats/${userId}`);
    chatListDuration.add(res.timings.duration);
    const ok = check(res, {
      'status 200': (r) => r.status === 200,
      'is array':   (r) => { try { return Array.isArray(JSON.parse(r.body)); } catch { return false; } },
    });
    errorRate.add(!ok);
  });

  sleep(randomBetween(0.2, 0.8));

  group('Load messages', () => {
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`);
    messagesDuration.add(res.timings.duration);
    const ok = check(res, {
      'status 200': (r) => r.status === 200,
    });
    errorRate.add(!ok);
  });

  sleep(randomBetween(0.3, 1.0));

  group('Search users', () => {
    const queries = ['Test', 'User', 'Load', '1', '2'];
    const q = queries[Math.floor(Math.random() * queries.length)];
    const res = http.get(`${BASE_URL}/users/search?q=${q}&selfId=${userId}`);
    searchDuration.add(res.timings.duration);
    const ok = check(res, {
      'status 200': (r) => r.status === 200,
    });
    errorRate.add(!ok);
  });

  sleep(randomBetween(0.2, 0.5));

  group('Presence check', () => {
    // Check presence of a random subset of users
    const subset = TEST_USER_IDS.slice(0, 10).join(',');
    const res = http.get(`${BASE_URL}/users/presence?ids=${subset}`);
    check(res, {
      'presence status 200': (r) => r.status === 200,
    });
  });

  sleep(randomBetween(0.5, 1.5));

  group('Get group members', () => {
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/members`);
    check(res, {
      'members status 200': (r) => r.status === 200,
    });
  });

  sleep(randomBetween(1, 2));
}

function randomBetween(min, max) {
  return Math.random() * (max - min) + min;
}
