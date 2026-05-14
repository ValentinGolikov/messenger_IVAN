/**
 * websocket.js — нагрузочный тест WebSocket соединений.
 *
 * Использует k6 execution segments для одновременного подключения
 * множества пользователей и удержания соединений.
 *
 * Запуск:
 *   k6 run --env BASE_URL=http://localhost:8081 websocket.js
 */

import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import { BASE_URL, WS_URL, TEST_USER_IDS, TEST_GROUP_CHAT_ID } from './config.js';

// Custom metrics
const wsConnectDuration  = new Trend('ws_connect_duration');
const wsMessagesSent     = new Counter('ws_messages_sent');
const wsMessagesReceived = new Counter('ws_messages_received');
const wsErrors           = new Rate('ws_errors');
const wsActiveConnections = new Gauge('ws_active_connections');

// Config
const VU_COUNT = parseInt(__ENV.USER_COUNT || '50');
const DURATION_SEC = parseDurationSec(__ENV.DURATION || '1m');

export const options = {
  scenarios: {
    // All VUs connect simultaneously and hold connections
    websocket_load: {
      executor: 'constant-vus',
      vus: VU_COUNT,
      duration: `${DURATION_SEC}s`,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    ws_errors:           ['rate<0.05'],   // <5% WS errors
    ws_connect_duration: ['p(95)<2000'],  // connect under 2s
  },
};

export default function () {
  // Each VU gets a unique user ID based on its number
  const userId = TEST_USER_IDS[(__VU - 1) % TEST_USER_IDS.length];
  const wsEndpoint = `${WS_URL}/chat/${userId}`;

  const startTime = Date.now();

  const res = ws.connect(wsEndpoint, { tags: { user_id: String(userId) } }, function (socket) {
    const connectTime = Date.now() - startTime;
    wsConnectDuration.add(connectTime);
    wsActiveConnections.add(1);

    let msgCount = 0;
    let typingCount = 0;

    socket.on('open', () => {
      // Send messages periodically
      socket.setInterval(() => {
        const msg = JSON.stringify({
          chatId: TEST_GROUP_CHAT_ID,
          text: `LoadTest user=${userId} msg=${++msgCount} ts=${Date.now()}`,
          timestamp: Date.now(),
        });
        socket.send(msg);
        wsMessagesSent.add(1);
      }, 2000); // Every 2 seconds

      // Send typing indicator occasionally
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
        typingCount++;
      }, 5000); // Every 5 seconds
    });

    socket.on('message', (data) => {
      wsMessagesReceived.add(1);
      try {
        const envelope = JSON.parse(data);
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
      console.error(`WS error user=${userId}: ${e.error()}`);
    });

    socket.on('close', () => {
      wsActiveConnections.add(-1);
    });
  });

  check(res, {
    'WS connected (101)': (r) => r && r.status === 101,
  });
}

/** Parse k6 duration string to seconds */
function parseDurationSec(d) {
  const match = d.match(/^(\d+)(ms|s|m|h)$/);
  if (!match) return 60;
  const val = parseInt(match[1]);
  switch (match[2]) {
    case 'ms': return Math.ceil(val / 1000);
    case 's':  return val;
    case 'm':  return val * 60;
    case 'h':  return val * 3600;
    default:   return 60;
  }
}
