/**
 * seed.js — создаёт тестовые данные перед нагрузочными тестами.
 *
 * Запуск (один раз перед тестами):
 *   k6 run --env BASE_URL=http://localhost:8081 seed.js
 *
 * Что делает:
 *  1. Регистрирует 20 тестовых пользователей через /login
 *     (сервер делает upsert по yandexId, поэтому повторный запуск безопасен)
 *  2. Создаёт групповой чат с первым пользователем как owner
 *  3. Добавляет всех остальных пользователей в чат через invite
 *  4. Создаёт несколько DM-чатов между парами пользователей
 *  5. Выводит userId-ы и chatId для использования в тестах
 *
 * ВАЖНО: /login требует реального Yandex OAuth токена.
 * Поскольку у нас нет реальных токенов для тестов, seed.js напрямую
 * вставляет пользователей через специальный тестовый эндпоинт.
 *
 * Если тестового эндпоинта нет — добавь его только в staging-сборку
 * (см. комментарий ниже).
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// Количество тестовых пользователей для создания
const USER_COUNT = 20;

export const options = {
  vus: 1,
  iterations: 1,
};

export default function () {
  console.log(`Seeding ${USER_COUNT} test users at ${BASE_URL}...`);

  const userIds = [];

  for (let i = 1; i <= USER_COUNT; i++) {
    // POST /test/seed/user — тестовый эндпоинт (добавь его в staging, см. TestRoutes.kt)
    const res = http.post(
      `${BASE_URL}/test/seed/user`,
      JSON.stringify({
        yandexId: `test_user_${i}`,
        displayName: `TestUser${i}`,
        realName: `Test User ${i}`,
        email: `testuser${i}@example.com`,
      }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    const ok = check(res, {
      [`user ${i} created (200 or 409)`]: (r) => r.status === 200 || r.status === 409,
    });

    if (res.status === 200) {
      const body = JSON.parse(res.body);
      userIds.push(body.userId);
      console.log(`  Created user ${i}: userId=${body.userId}`);
    } else if (res.status === 409) {
      // Already exists — get existing ID
      const existing = http.get(`${BASE_URL}/test/seed/user?yandexId=test_user_${i}`);
      if (existing.status === 200) {
        const body = JSON.parse(existing.body);
        userIds.push(body.userId);
        console.log(`  User ${i} already exists: userId=${body.userId}`);
      }
    } else {
      console.error(`  Failed to create user ${i}: ${res.status} ${res.body}`);
    }

    sleep(0.05);
  }

  if (userIds.length === 0) {
    console.error('No users created. Make sure the staging test endpoint is available.');
    return;
  }

  // Create a group chat with user[0] as owner
  const groupRes = http.post(
    `${BASE_URL}/chats/group`,
    `userId=${userIds[0]}&title=LoadTestGroup`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );

  check(groupRes, { 'group chat created': (r) => r.status === 200 });

  let groupChatId = null;
  let inviteToken = null;

  if (groupRes.status === 200) {
    const body = JSON.parse(groupRes.body);
    groupChatId = body.chatId;
    inviteToken = body.inviteToken;
    console.log(`\nCreated group chat: chatId=${groupChatId}, inviteToken=${inviteToken}`);
  }

  // Add all other users to the group via invite token
  if (inviteToken) {
    for (let i = 1; i < userIds.length; i++) {
      const joinRes = http.post(
        `${BASE_URL}/invites/${inviteToken}/join`,
        `userId=${userIds[i]}`,
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
      );
      check(joinRes, { [`user ${i + 1} joined group`]: (r) => r.status === 200 });
      sleep(0.05);
    }
    console.log(`All ${userIds.length} users joined group chat ${groupChatId}`);
  }

  // Create a few DM chats between pairs
  const dmPairs = [
    [userIds[0], userIds[1]],
    [userIds[0], userIds[2]],
    [userIds[1], userIds[2]],
  ];

  for (const [a, b] of dmPairs) {
    const dmRes = http.post(
      `${BASE_URL}/chats/dm`,
      `userId=${a}&otherUserId=${b}`,
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    );
    check(dmRes, { 'DM created': (r) => r.status === 200 });
    if (dmRes.status === 200) {
      const body = JSON.parse(dmRes.body);
      console.log(`  DM between ${a} and ${b}: chatId=${body.chatId}`);
    }
    sleep(0.05);
  }

  console.log('\n=== Seed complete ===');
  console.log('User IDs:', JSON.stringify(userIds));
  console.log('Group chat ID:', groupChatId);
  console.log('\nUpdate TEST_USER_IDS and TEST_GROUP_CHAT_ID in config.js if needed.');
}
