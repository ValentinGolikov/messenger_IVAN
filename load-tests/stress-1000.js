/**
 * stress-1000.js — extreme stress test: 500 → 1000 VUs
 *
 * Сценарий:
 *  - Постепенный разгон от 500 до 1000 VU
 *  - Удержание пика 1000 VU в течение 3 минут
 *  - Мониторинг точки деградации (где latency начинает расти)
 *  - Мягкие пороги — тест не прерывается при превышении
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 --out influxdb=http://localhost:8086/k6 stress-1000.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, TEST_USER_IDS, TEST_GROUP_CHAT_ID } from './config.js';

// Custom metrics per endpoint
const chatListDuration   = new Trend('chat_list_duration');
const messagesDuration   = new Trend('messages_duration');
const searchDuration     = new Trend('search_duration');
const globalDuration     = new Trend('global_search_duration');
const presenceDuration   = new Trend('presence_duration');
const errorRate          = new Rate('error_rate');
const degradationCounter = new Counter('degradation_events'); // requests over 1s

export const options = {
  stages: [
    { duration: '30s', target: 100  }, // warm up
    { duration: '30s', target: 500  }, // previous baseline
    { duration: '1m',  target: 1000  }, // new territory
    { duration: '1m',  target: 2000  }, // pushing hard
    { duration: '2m',  target: 3000 }, // peak load
    { duration: '1m',  target: 1500  }, // step down
    { duration: '30s', target: 0    }, // cool down
  ],
  thresholds: {
    // Soft thresholds — test continues even if breached, we just record it
    'http_req_failed':        [{ threshold: 'rate<0.20', abortOnFail: false }],
    'http_req_duration':      [{ threshold: 'p(95)<5000', abortOnFail: false }],
    'chat_list_duration':     [{ threshold: 'p(95)<3000', abortOnFail: false }],
    'messages_duration':      [{ threshold: 'p(95)<3000', abortOnFail: false }],
    'error_rate':             [{ threshold: 'rate<0.20', abortOnFail: false }],
  },
};

export default function () {
  const userId = TEST_USER_IDS[(__VU - 1) % TEST_USER_IDS.length];

  // Weighted scenario mix — same as stress.js
  const scenario = Math.random();

  if (scenario < 0.40) {
    // 40% — chat list (most common, hits Redis cache)
    const res = http.get(`${BASE_URL}/chats/${userId}`);
    const duration = res.timings.duration;
    chatListDuration.add(duration);
    if (duration > 1000) degradationCounter.add(1);
    const ok = check(res, { 'chat list ok': (r) => r.status === 200 });
    errorRate.add(!ok);

  } else if (scenario < 0.70) {
    // 30% — messages (hits Cassandra)
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`);
    const duration = res.timings.duration;
    messagesDuration.add(duration);
    if (duration > 1000) degradationCounter.add(1);
    const ok = check(res, { 'messages ok': (r) => r.status === 200 });
    errorRate.add(!ok);

  } else if (scenario < 0.85) {
    // 15% — user search (hits Postgres)
    const letters = ['a', 'e', 'i', 'o', 'u', 't', 's', 'r'];
    const q = letters[Math.floor(Math.random() * letters.length)];
    const res = http.get(`${BASE_URL}/users/search?q=${q}&selfId=${userId}`);
    const duration = res.timings.duration;
    searchDuration.add(duration);
    if (duration > 1000) degradationCounter.add(1);
    const ok = check(res, { 'search ok': (r) => r.status === 200 });
    errorRate.add(!ok);

  } else if (scenario < 0.95) {
    // 10% — presence check (hits Redis — should be fast)
    const ids = TEST_USER_IDS.slice(0, 10).join(',');
    const res = http.get(`${BASE_URL}/users/presence?ids=${ids}`);
    const duration = res.timings.duration;
    presenceDuration.add(duration);
    if (duration > 1000) degradationCounter.add(1);
    const ok = check(res, { 'presence ok': (r) => r.status === 200 });
    errorRate.add(!ok);

  } else {
    // 5% — global search (most expensive: Cassandra + Postgres)
    const res = http.get(`${BASE_URL}/search/global?q=test&userId=${userId}`);
    const duration = res.timings.duration;
    globalDuration.add(duration);
    if (duration > 1000) degradationCounter.add(1);
    const ok = check(res, { 'global search ok': (r) => r.status === 200 });
    errorRate.add(!ok);
  }

  // Minimal think time — maximum pressure
  sleep(0.1);
}
