/**
 * websocket.js — нагрузочный тест WebSocket соединений.
 *
 * Сценарий:
 *  - N пользователей одновременно подключаются к ws://.../chat/{userId}
 *  - Каждый периодически отправляет сообщения в групповой чат
 *  - Проверяется что сообщения доставляются (через incoming frames)
 *  - Тест длится DURATION, затем все соединения закрываются
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 websocket.js
 */

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, WS_URL, VU_COUNT, DURATION, TEST_USER_IDS, TEST_GROUP_CHAT_ID, COMMON_THRESHOLDS } from './config.js';

const wsConnectDuration  = new Trend('ws_connect_duration');
const wsMessagesSent     = new Counter('ws_messages_sent');
const wsMessagesReceived = new Counter('ws_messages_received');
const wsErrors           = new Rate('ws_errors');

export const options = {
  stages: [
    { duration: '20s', target: Math.floor(VU_COUNT * 0.5) },
    { duration: '30s', target: VU_COUNT },
    { duration: DURATION, target: VU_COUNT },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    ws_errors:           ['rate<0.05'],   // <5% WS errors
    ws_connect_duration: ['p(95)<2000'],  // connect under 2s
  },
};

export default function () {
  const userId = TEST_USER_IDS[__VU % TEST_USER_IDS.length];
  const wsEndpoint = `${WS_URL}/chat/${userId}`;

  const startTime = Date.now();

  const res = ws.connect(wsEndpoint, {}, function (socket) {
    wsConnectDuration.add(Date.now() - startTime);

    socket.on('open', () => {
      // Send a message every 3-7 seconds
      socket.setInterval(() => {
        const msg = JSON.stringify({
          chatId: TEST_GROUP_CHAT_ID,
          text: `Load test message from user ${userId} at ${Date.now()}`,
          timestamp: Date.now(),
        });
        socket.send(msg);
        wsMessagesSent.add(1);
      }, randomBetween(3000, 7000));

      // Send a typing indicator occasionally
      socket.setInterval(() => {
        const envelope = JSON.stringify({
          type: 'typing',
          payload: JSON.stringify({
            chatId: TEST_GROUP_CHAT_ID,
            userId: userId,
            typing: true,
          }),
        });
        socket.send(envelope);
      }, randomBetween(10000, 20000));
    });

    socket.on('message', (data) => {
      wsMessagesReceived.add(1);
      try {
        const envelope = JSON.parse(data);
        // Validate envelope structure
        check(envelope, {
          'envelope has type':    (e) => typeof e.type === 'string',
          'envelope has payload': (e) => typeof e.payload === 'string',
        });
      } catch (e) {
        wsErrors.add(1);
      }
    });

    socket.on('error', (e) => {
      wsErrors.add(1);
      console.error(`WS error for user ${userId}: ${e.error()}`);
    });

    // Hold connection for the test duration
    socket.setTimeout(() => {
      socket.close();
    }, parseDurationMs(DURATION) + 10000);
  });

  check(res, {
    'WS connected (101)': (r) => r && r.status === 101,
  });
}

function randomBetween(min, max) {
  return Math.floor(Math.random() * (max - min) + min);
}

/** Parse k6 duration string like "2m", "30s", "1h" to milliseconds */
function parseDurationMs(d) {
  const match = d.match(/^(\d+)(ms|s|m|h)$/);
  if (!match) return 120000; // default 2m
  const val = parseInt(match[1]);
  switch (match[2]) {
    case 'ms': return val;
    case 's':  return val * 1000;
    case 'm':  return val * 60 * 1000;
    case 'h':  return val * 3600 * 1000;
    default:   return 120000;
  }
}
