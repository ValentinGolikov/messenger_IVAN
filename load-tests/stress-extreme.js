/**
 * stress-extreme.js — Экстремальный стресс-тест для ночного прогона
 *
 * Цель: Найти реальную точку отказа системы
 *
 * Параметры:
 *   - Начальная нагрузка: 5000 VU
 *   - Рост до 50000 VU за 30 минут
 *   - Без think time — максимальное давление
 *   - Порог ошибок: 25%
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 stress-extreme.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { BASE_URL, TEST_USER_IDS, TEST_GROUP_CHAT_ID } from './config.js';

// Custom metrics
const chatListDuration   = new Trend('chat_list_duration');
const messagesDuration   = new Trend('messages_duration');
const searchDuration     = new Trend('search_duration');
const globalDuration     = new Trend('global_search_duration');
const presenceDuration   = new Trend('presence_duration');
const errorRate          = new Rate('error_rate');
const breakingPoint      = new Gauge('breaking_point_vus');
const degradationEvents  = new Counter('degradation_events');
const timeoutErrors      = new Counter('timeout_errors');
const connectionErrors   = new Counter('connection_errors');

// Configuration
const INITIAL_VUS = parseInt(__ENV.INITIAL_VUS || '5000');
const ERROR_THRESHOLD = 0.25;

export const options = {
  // Экстремальный рост нагрузки
  stages: [
    // Быстрый разгон до 5000 VU
    { duration: '2m', target: INITIAL_VUS * 0.2 },   // 1000 VU
    { duration: '3m', target: INITIAL_VUS * 0.5 },   // 2500 VU
    { duration: '5m', target: INITIAL_VUS },         // 5000 VU

    // Агрессивный рост до 20000 VU
    { duration: '10m', target: INITIAL_VUS * 2 },    // 10000 VU
    { duration: '10m', target: INITIAL_VUS * 4 },    // 20000 VU

    // Экстремальный рост до 50000 VU
    { duration: '15m', target: INITIAL_VUS * 6 },    // 30000 VU
    { duration: '15m', target: INITIAL_VUS * 8 },    // 40000 VU
    { duration: '10m', target: INITIAL_VUS * 10 },   // 50000 VU

    // Завершение
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    // Жёсткий порог для стресс-теста — 25%
    http_req_failed: [{ threshold: 'rate<0.25', abortOnFail: true }],
    error_rate:      [{ threshold: 'rate<0.25', abortOnFail: true }],

    // Мягкие пороги
    http_req_duration:   [{ threshold: 'p(95)<5000' }],
  },
};

// Хук для отслеживания breaking point
export function handleSummary(data) {
  const finalVUs = data.metrics.vus?.values?.max || 0;
  const errorRateValue = data.metrics.error_rate?.values?.rate || 0;
  const timeoutCount = data.metrics.timeout_errors?.values?.count || 0;
  const connErrors = data.metrics.connection_errors?.values?.count || 0;

  console.log(`\n${'='.repeat(70)}`);
  console.log(`ЭКСТРЕМАЛЬНЫЙ СТРЕСС-ТЕСТ ЗАВЕРШЁН`);
  console.log(`${'='.repeat(70)}`);
  console.log(`Максимальное VU: ${finalVUs}`);
  console.log(`Финальный уровень ошибок: ${(errorRateValue * 100).toFixed(2)}%`);
  console.log(`Таймауты: ${timeoutCount}`);
  console.log(`Ошибки соединения: ${connErrors}`);
  console.log(`Порог ошибок: ${(ERROR_THRESHOLD * 100)}%`);

  if (errorRateValue >= ERROR_THRESHOLD) {
    console.log(`\n⚠️ ТОЧКА ОТКАЗА НАЙДЕНА: ${finalVUs} VU`);
    breakingPoint.add(finalVUs);
  } else {
    console.log(`\n✓ Система выдержала нагрузку до ${finalVUs} VU`);
    console.log(`  Рекомендуется увеличить нагрузку или проверить ресурсы сервера`);
  }

  console.log(`${'='.repeat(70)}\n`);

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export default function () {
  const userId = TEST_USER_IDS[(__VU - 1) % TEST_USER_IDS.length];

  // Агрессивные параметры — короткий таймаут для быстрого обнаружения проблем
  const params = {
    timeout: '10s',
    throw: false,
  };

  // Взвешенное распределение сценариев
  const scenario = Math.random();

  if (scenario < 0.35) {
    // 35% — список чатов
    const res = http.get(`${BASE_URL}/chats/${userId}`, params);
    chatListDuration.add(res.timings.duration);
    const ok = check(res, {
      'chat list 200': (r) => r.status === 200,
      'chat list no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) {
      degradationEvents.add(1);
      if (res.error_code !== 0) connectionErrors.add(1);
    }

  } else if (scenario < 0.60) {
    // 25% — загрузка сообщений
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`, params);
    messagesDuration.add(res.timings.duration);
    const ok = check(res, {
      'messages 200': (r) => r.status === 200,
      'messages no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) {
      degradationEvents.add(1);
      if (res.error_code !== 0) connectionErrors.add(1);
    }

  } else if (scenario < 0.80) {
    // 20% — поиск пользователей
    const letters = ['a', 'e', 'i', 'o', 'u', 't', 's', 'r', 'n', 'l'];
    const q = letters[Math.floor(Math.random() * letters.length)];
    const res = http.get(`${BASE_URL}/users/search?q=${q}&selfId=${userId}`, params);
    searchDuration.add(res.timings.duration);
    const ok = check(res, {
      'search 200': (r) => r.status === 200,
      'search no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) {
      degradationEvents.add(1);
      if (res.error_code !== 0) connectionErrors.add(1);
    }

  } else if (scenario < 0.92) {
    // 12% — проверка присутствия
    const ids = TEST_USER_IDS.slice(0, 10).join(',');
    const res = http.get(`${BASE_URL}/users/presence?ids=${ids}`, params);
    presenceDuration.add(res.timings.duration);
    const ok = check(res, {
      'presence 200': (r) => r.status === 200,
      'presence no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) {
      degradationEvents.add(1);
      if (res.error_code !== 0) connectionErrors.add(1);
    }

  } else {
    // 8% — глобальный поиск (самый тяжёлый)
    const queries = ['test', 'user', 'message', 'chat', 'group'];
    const q = queries[Math.floor(Math.random() * queries.length)];
    const res = http.get(`${BASE_URL}/search/global?q=${q}&userId=${userId}`, params);
    globalDuration.add(res.timings.duration);
    const ok = check(res, {
      'global search 200': (r) => r.status === 200,
      'global search no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) {
      degradationEvents.add(1);
      if (res.error_code !== 0) connectionErrors.add(1);
    }
  }

  // Без think time — максимальное давление
}
