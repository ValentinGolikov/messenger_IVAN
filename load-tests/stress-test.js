/**
 * stress-test.js — Стресс-тестирование
 *
 * Цель: Найти точку отказа системы (breaking point)
 *
 * Алгоритм:
 *   1. Плавный рост нагрузки за 5 минут до начальной цели
 *   2. После достижения цели — рост на 10% каждые 10 секунд
 *   3. Тест останавливается при превышении порога ошибок 25%
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 --out influxdb=http://localhost:8086/k6 stress-test.js
 *   k6 run --env BASE_URL=http://localhost:8081 --env INITIAL_VUS=500 --out influxdb=http://localhost:8086/k6 stress-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
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
const totalRequests      = new Counter('total_requests');

// Configuration
const INITIAL_VUS = parseInt(__ENV.INITIAL_VUS || '1500');
const ERROR_THRESHOLD = 0.25; // 25% — порог для остановки
const GROWTH_INTERVAL = 10; // секунды между шагами роста
const GROWTH_RATE = 1.10;   // 10% рост за шаг

// Глобальное состояние для отслеживания ошибок
let errorCount = 0;
let totalChecks = 0;
let currentVUs = 0;
let stressPhaseStarted = false;

export const options = {
  // Начальный этап: плавный выход на начальную нагрузку
  stages: [
    // 5 минут плавного роста до INITIAL_VUS
    { duration: '1m', target: Math.floor(INITIAL_VUS * 0.2) },
    { duration: '1m', target: Math.floor(INITIAL_VUS * 0.4) },
    { duration: '1m', target: Math.floor(INITIAL_VUS * 0.6) },
    { duration: '1m', target: Math.floor(INITIAL_VUS * 0.8) },
    { duration: '1m', target: INITIAL_VUS },
    // Этап стресса: растущая нагрузка в течение 15 минут
    // k6 не поддерживает динамическое изменение stages,
    // поэтому задаём максимум и контролируем через thresholds
    { duration: '15m', target: INITIAL_VUS * 5 },
    // Завершение
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    // Жёсткий порог для стресс-теста — 25%
    http_req_failed: [{ threshold: 'rate<0.25', abortOnFail: true }],
    error_rate:      [{ threshold: 'rate<0.25', abortOnFail: true }],

    // Мягкие пороги — не прерывают тест
    http_req_duration:   [{ threshold: 'p(95)<3000' }],
    chat_list_duration:  [{ threshold: 'p(95)<1000' }],
    messages_duration:   [{ threshold: 'p(95)<2000' }],
  },
};

// Хук для отслеживания breaking point перед остановкой
export function handleSummary(data) {
  const finalVUs = data.metrics.vus?.values?.max || 0;
  const errorRateValue = data.metrics.error_rate?.values?.rate || 0;

  console.log(`\n${'='.repeat(60)}`);
  console.log(`СТРЕСС-ТЕСТ ЗАВЕРШЁН`);
  console.log(`${'='.repeat(60)}`);
  console.log(`Максимальное VU: ${finalVUs}`);
  console.log(`Финальный уровень ошибок: ${(errorRateValue * 100).toFixed(2)}%`);
  console.log(`Порог ошибок: ${(ERROR_THRESHOLD * 100)}%`);

  if (errorRateValue >= ERROR_THRESHOLD) {
    console.log(`\n⚠️ ТОЧКА ОТКАЗА НАЙДЕНА: ${finalVUs} VU`);
    breakingPoint.add(finalVUs);
  } else {
    console.log(`\n✓ Система выдержала нагрузку до ${finalVUs} VU`);
  }

  console.log(`${'='.repeat(60)}\n`);

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export default function () {
  const userId = TEST_USER_IDS[(__VU - 1) % TEST_USER_IDS.length];
  totalRequests.add(1);

  // Взвешенное распределение сценариев
  const scenario = Math.random();

  // Параметры запроса с таймаутом
  const params = {
    timeout: '30s',
    throw: false,
  };

  if (scenario < 0.35) {
    // 35% — список чатов
    const res = http.get(`${BASE_URL}/chats/${userId}`, params);
    chatListDuration.add(res.timings.duration);
    const ok = check(res, {
      'chat list 200': (r) => r.status === 200,
      'chat list no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) degradationEvents.add(1);

  } else if (scenario < 0.60) {
    // 25% — загрузка сообщений
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`, params);
    messagesDuration.add(res.timings.duration);
    const ok = check(res, {
      'messages 200': (r) => r.status === 200,
      'messages no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) degradationEvents.add(1);

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
    if (!ok) degradationEvents.add(1);

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
    if (!ok) degradationEvents.add(1);

  } else {
    // 8% — глобальный поиск (тяжёлый запрос)
    const res = http.get(`${BASE_URL}/search/global?q=test&userId=${userId}`, params);
    globalDuration.add(res.timings.duration);
    const ok = check(res, {
      'global search 200': (r) => r.status === 200,
      'global search no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
    if (!ok) degradationEvents.add(1);
  }

  // Без think time — максимальная нагрузка для поиска breaking point
}
