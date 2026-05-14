# Load Tests

Нагрузочные тесты для messenger backend на базе [k6](https://k6.io/).

## Установка k6

```bash
# Windows (winget)
winget install k6 --source winget

# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

## Запуск staging-окружения

```bash
cd backend

# Первый запуск — сборка образа занимает несколько минут
docker compose -f docker-compose.staging.yml up -d --build

# Дождаться готовности (смотреть логи)
docker compose -f docker-compose.staging.yml logs -f app-staging
```

Сервис будет доступен на `http://localhost:8081`.

## Запуск тестов

```bash
cd load-tests

# Smoke test — быстрая проверка что всё работает (1 пользователь, 30 сек)
k6 run smoke.js

# REST API — нагрузка на HTTP эндпоинты
k6 run --env BASE_URL=http://localhost:8081 rest-api.js

# WebSocket — нагрузка на WS соединения и обмен сообщениями
k6 run --env BASE_URL=http://localhost:8081 websocket.js

# Полный сценарий (REST + WS вместе)
k6 run --env BASE_URL=http://localhost:8081 full-scenario.js

# Стресс-тест (постепенное увеличение нагрузки)
k6 run --env BASE_URL=http://localhost:8081 stress.js
```

## Параметры

| Переменная    | По умолчанию            | Описание                        |
|---------------|-------------------------|---------------------------------|
| `BASE_URL`    | `http://localhost:8081` | Адрес тестируемого сервиса      |
| `USER_COUNT`  | `50`                    | Количество виртуальных юзеров   |
| `DURATION`    | `2m`                    | Длительность теста              |

Пример с параметрами:
```bash
k6 run --env BASE_URL=http://localhost:8081 --env USER_COUNT=100 --env DURATION=5m rest-api.js
```

## Очистка staging

```bash
# Остановить и удалить все данные staging
docker compose -f docker-compose.staging.yml down -v
```

## Важно

- **Никогда не запускай тесты против прода** (`http://localhost:8080` или реального сервера)
  без явного намерения — тесты создают сотни пользователей и тысячи сообщений.
- Аутентификация через Yandex OAuth не мокируется — тесты используют прямые вызовы
  к `/login` с заранее созданными тестовыми пользователями через seed-скрипт.
- Перед тестами запусти `seed.js` для создания тестовых данных.
