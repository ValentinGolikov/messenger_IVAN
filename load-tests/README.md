# Load Tests

Нагрузочные тесты для messenger backend на базе [k6](https://k6.io/).

## Установка k6

```bash
# Windows (winget)
winget install k6 --source winget
```

Сервис будет доступен на `http://localhost:8081`.

---

## Запуск с кластером Cassandra 

### Шаг 1 — Настроить WSL

В файле `C:\Users\<user>\.wslconfig`:

```ini
[wsl2]
memory=16GB
processors=6
swap=6GB
```

P.S. Можно выставить побольше, например 24GB, для тестов должно хватить и 16GB

Применить:

```powershell
wsl --shutdown
# подождать 10 секунд
wsl
```

### Шаг 2 — Запустить кластер Cassandra

```bash
cd messenger_IVAN/

# Запустить 3 ноды (ноды стартуют последовательно, ~3-5 минут)
docker compose -f backend/docker-compose.cassandra-cluster.yml up -d
```

### Шаг 3 — Запустить приложение

```bash
docker compose -f backend/docker-compose.staging-cluster.yml --env-file backend/.env.staging up -d --build
docker compose -f backend/docker-compose.staging.yml up -d
```

### Шаг 4 (дополнительно) - Запустить Grafana

```bash
docker compose -f load-tests/docker-compose.grafana.yml up -d
```

Grafana будет запущена на [localhost](http://localhost:3000). 
Там нужно создать Data Source в разделе Connections:
1. Add new Data Source
2. Выбираем InfluxDB
3. URL: http://influxdb:8086
4. Database: k6
5. Затем Save & Test

---

После создания Data Source импортируем Dashboard
В поле для JSON файла вставить код из файла [grafana.json](./grafana.json) и импортировать DashBoard. На DashBoard указать сверху Last 15 minutes и Refresh каждые 5 секунд

### Остановка кластера

```bash
# Остановить приложение
docker compose -f backend/docker-compose.staging-cluster.yml down
docker compose -f backend/docker-compose.staging.yml down

# Остановить кластер Cassandra
docker compose -f backend/docker-compose.cassandra-cluster.yml down

# Остановить Grafana
docker compose -f load-tests/docker-compose.grafana.yml down
```

---

## Запуск тестов

Производится с хоста

```bash
cd load-tests

# Стресс-тест (до 300 VU)
.\run-test.ps1 -TestName stress

# Нагрузочный тест (15+ минут, до 500 VU)
.\run-test.ps1 -TestName load-test -TargetVus 500

# Стресс-тест (поиск точки отказа, до 500+ VU)
.\run-test.ps1 -TestName stress-test -InitialVus 500
```

### Типы тестов

| Тест | Длительность | VU | Порог ошибок | Цель |
|------|-------------|-----|-------------|------|
| `smoke` | 2 мин | 100 | 1% | Базовая проверка работоспособности |
| `rest-api` | 5 мин | 300 | 1% | REST API endpoints |
| `websocket` | 5 мин | 300 | 1% | WebSocket соединения |
| `stress` | 5 мин | 300 | 10% | Стресс до 300 VU |
| `load-test` | 15+ мин | до 500+ | 10% | Плавная нагрузка 15+ минут |
| `stress-test` | 20+ мин | до 15000+ | 25% | Поиск точки отказа |

### Для высоконагруженных тестов (6000+ RPS)

При тестировании с высоким RPS может появляться предупреждение:
```
WARN The flush operation took higher than the expected set push interval
```

**Решение:** Перезапустить InfluxDB с увеличенными ресурсами:
```powershell
docker compose -f load-tests/docker-compose.grafana.yml down
docker compose -f load-tests/docker-compose.grafana.yml up -d
```

Или запустить тест без отправки метрик в Grafana:
```bash
k6 run --env BASE_URL=http://localhost:8081 stress-test.js
# без --out influxdb=...
```

### Параметры скрипта

| Параметр | Описание | Пример |
|----------|----------|--------|
| `-TestName` | Имя теста (без .js) | `-TestName load-test` |
| `-UserCount` | Количество VU для базовых тестов | `-UserCount 500` |
| `-Duration` | Длительность теста | `-Duration 10m` |
| `-BaseUrl` | URL сервера | `-BaseUrl http://localhost:8081` |
| `-TargetVus` | Целевое VU для load-test | `-TargetVus 500` |
| `-InitialVus` | Начальное VU для stress-test | `-InitialVus 500` |

### Примеры

```powershell
# Нагрузочный тест с 500 VU
.\run-test.ps1 -TestName load-test -TargetVus 500

# Стресс-тест с начальной нагрузкой 1000 VU
.\run-test.ps1 -TestName stress-test -InitialVus 1000

# Кастомный стресс-тест
.\run-test.ps1 -TestName stress -UserCount 1000 -Duration 10m
```

### Результаты

Каждый тест сохраняет результат в файл:
```
<test_name>_result_<HH_mm_ss>.txt
```

Файл содержит:
- Параметры запуска
- Полный вывод k6
- Пороги и метрики
