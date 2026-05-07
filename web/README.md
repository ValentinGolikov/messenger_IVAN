# messenger_IVAN — web

Веб-клиент мессенджера (React + Vite), интегрированный с backend API и WebSocket. Актуальная ветка для релиза `v0.1`: `main`.

## Что реализовано

- Вход через Яндекс OAuth (frontend получает `access_token` и отправляет его на backend).
- Обычный вход по логину и паролю:
  - `POST /auth/password/login`
  - `POST /auth/password/register`
- Авторизация через backend endpoint:
  - `POST /login`
- Работа с чатами через backend:
  - `GET /chats/{userId}`
  - `GET /chats/{chatId}/messages`
  - `POST /chats/dm`
  - `GET /users/search?q=...&selfId=...`
- Real-time сообщения через WebSocket:
  - `WS /chat/{userId}`
- Offline/retry поведение на фронте:
  - локальная очередь неотправленных сообщений в `localStorage`
  - автоматическая отправка после восстановления соединения
- UI-функции:
  - список чатов, открытие диалога, отправка сообщений
  - поиск пользователей и создание DM
  - создание групповых чатов на уровне frontend-прототипа
  - честный статус шифрования: UI показывает, что E2E не активировано без серверного протокола ключей
  - pending/failed/retry состояния отправки сообщений
  - собственный online от состояния браузера (`online/offline`)
  - темы (light/dark), настройки, локальная кастомизация
  - выход без удаления локальных пользовательских настроек и сохраненного имени

## Конфигурация

Используются переменные окружения:

- `VITE_API_URL` (пример: `https://titlo10.fun:8080`)
- `VITE_YANDEX_CLIENT_ID`
- `VITE_REDIRECT_URI` (пример: `https://<frontend-domain>/auth/callback`)

Пример production-env:
- `.env.production.example`

## Локальный запуск

```bash
npm install
npm run dev -- --host 0.0.0.0 --port 3000
```

## Docker/Deploy

Ветка содержит готовый deploy-пакет:

- `Dockerfile`
- `docker-compose.yml`
- `deploy/nginx/messenger.conf`
- `DEPLOY.md` (пошаговая инструкция)

Быстрый запуск:

```bash
docker compose up -d --build
```

## Требования к backend для корректной работы web-клиента

1. CORS allowlist для frontend-домена.
2. Поддержка `OPTIONS` preflight.
3. OAuth callback в Яндексе: `https://<frontend-domain>/auth/callback`.
4. Доступный WebSocket upgrade для `/chat/{userId}`.
5. Для настоящих групповых чатов: endpoints создания группы, участников и ролей.
6. Для online контактов: WebSocket/API presence (`online`, `offline`, `last_seen`, heartbeat).

## Ограничения текущей ветки

Следующие пункты требуют серверной реализации/доработок и не закрываются только frontend-изменениями:

- криптографически реальное E2EE: device keys, key exchange, group keys, key verification, key rotation,
- push-уведомления и anti-storm,
- удаление аккаунта с grace period,
- расширенная групповая ролевая модель (админ-действия),
- полноценные server-side delivered/read/last-seen статусы.
