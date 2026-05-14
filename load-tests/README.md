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
docker compose -f backend/docker-compose.staging-cluster.yml --env-file .env.staging up -d --build
docker compose -f backend/docker-compose.staging.yml up -d
```

### Шаг 4 (дополнительно) - Запустить Grafana

```bash
docker compose -f load-test/docker-compose.grafana.yml up -d
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
```

Тест автоматически:
- Отправляет метрики в Grafana (InfluxDB)
- Сохраняет результат в файл `<test>_result_HH_mm_ss.txt`
