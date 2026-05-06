# messenger_IVAN — web_messenger

Ветка `web_messenger` содержит веб-клиент мессенджера (React + Vite), интегрированный с backend API и WebSocket.

## Что реализовано в этой ветке

- Вход через Яндекс OAuth (frontend получает `access_token` и отправляет его на backend).
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
  - темы (light/dark), настройки, локальная кастомизация
  - очистка локальных данных при выходе (`localStorage.clear()`)

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

## Ограничения текущей ветки

Следующие пункты требуют серверной реализации/доработок и не закрываются только frontend-изменениями:

- криптографически реальное E2EE (не только UI-маркер),
- push-уведомления и anti-storm,
- удаление аккаунта с grace period,
- расширенная групповая ролевая модель (админ-действия),
- полноценные server-side delivered/read/last-seen статусы.
