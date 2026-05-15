/**
 * load-test.js — Нагрузочное тестирование (15+ минут)
 *
 * Цель: Проверка стабильности под плановой нагрузкой
 *
 * Параметры:
 *   - Начальная нагрузка: 100 VU
 *   - Плавный рост до целевой: 5 минут
 *   - Время на плато: минимум 10 минут
 *   - Общая длительность: минимум 15 минут
 *   - Порог ошибок: 10%
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 --out influxdb=http://localhost:8086/k6 load-test.js
 *   k6 run --env BASE_URL=http://localhost:8081 --env TARGET_VUS=500 --out influxdb=http://localhost:8086/k6 load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, TEST_USER_IDS, TEST_GROUP_CHAT_ID } from './config.js';

// Metrics
const chatListDuration   = new Trend('chat_list_duration');
const messagesDuration   = new Trend('messages_duration');
const searchDuration     = new Trend('search_duration');
const globalDuration     = new Trend('global_search_duration');
const presenceDuration   = new Trend('presence_duration');
const errorRate          = new Rate('error_rate');

// Configuration
const TARGET_VUS = parseInt(__ENV.TARGET_VUS || '5000');

export const options = {
  stages: [
    // Плавный рост от 0 до TARGET_VUS за 5 минут
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.2) },  // 20% нагрузки за 1 мин
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.4) },  // 40% нагрузки за 2 мин
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.6) },  // 60% нагрузки за 3 мин
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.8) },  // 80% нагрузки за 4 мин
    { duration: '1m', target: TARGET_VUS },                    // 100% нагрузки за 5 мин

    // Плато — минимум 10 минут на целевой нагрузке
    { duration: '10m', target: TARGET_VUS },

    // Плавное снижение за 2 минуты
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.5) },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    // http_req_failed ловит только сетевые ошибки (connection refused, timeout)
    // Для HTTP 4xx/5xx используем кастомный error_rate
    http_req_failed: [{ threshold: 'rate<0.10', abortOnFail: true }],

    // Мягкие пороги — предупреждения, не прерывают тест
    http_req_duration:   [{ threshold: 'p(95)<1000', abortOnFail: false }],
    chat_list_duration:  [{ threshold: 'p(95)<500', abortOnFail: false }],
    messages_duration:   [{ threshold: 'p(95)<800', abortOnFail: false }],
    error_rate:          [{ threshold: 'rate<0.10', abortOnFail: true }],
  },
};

export default function () {
  const userId = TEST_USER_IDS[(__VU - 1) % TEST_USER_IDS.length];

  // Взвешенное распределение сценариев
  const scenario = Math.random();

  // Дополнительные проверки: таймауты и сетевые ошибки
  const params = {
    timeout: '30s',        // Максимальное время ожидания
    throw: false,          // Не выбрасывать исключение при ошибках
  };

  if (scenario < 0.35) {
    // 35% — список чатов (самый частый сценарий)
    const res = http.get(`${BASE_URL}/chats/${userId}`, params);
    chatListDuration.add(res.timings.duration);
    // Проверяем и HTTP статус, и отсутствие сетевой ошибки
    const ok = check(res, {
      'chat list 200': (r) => r.status === 200,
      'chat list no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);

  } else if (scenario < 0.60) {
    // 25% — загрузка сообщений
    const res = http.get(`${BASE_URL}/chats/${TEST_GROUP_CHAT_ID}/messages?userId=${userId}`, params);
    messagesDuration.add(res.timings.duration);
    const ok = check(res, {
      'messages 200': (r) => r.status === 200,
      'messages no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);

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

  } else {
    // 8% — глобальный поиск (тяжёлый запрос)
    const res = http.get(`${BASE_URL}/search/global?q=test&userId=${userId}`, params);
    globalDuration.add(res.timings.duration);
    const ok = check(res, {
      'global search 200': (r) => r.status === 200,
      'global search no error': (r) => r.error_code === 0,
    });
    errorRate.add(!ok);
  }

  // Реалистичный think time: 1-3 секунды между действиями
  sleep(1 + Math.random() * 2);
}
