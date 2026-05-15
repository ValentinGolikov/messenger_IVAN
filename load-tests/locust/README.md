# Locust Load Tests

[Locust](https://locust.io/) — масштабируемый инструмент нагрузочного тестирования на Python.

## Преимущества перед k6

| Особенность | k6 | Locust |
|-------------|-----|--------|
| Распределённая нагрузка | Сложно | Встроено |
| Web UI | Нет | Есть |
| Написание тестов | JavaScript | Python |
| Масштабирование | Ограничено | Неограничено |

## Установка

### Windows (PowerShell)

```powershell
pip install -r requirements.txt
```

### Linux/WSL

```bash
pip3 install -r requirements.txt
```

## Запуск

### 1. Интерактивный режим (Web UI)

```bash
locust -f locustfile.py --host=http://localhost:8081
```

Откройте http://localhost:8089 и укажите:
- Number of users: 10000
- Spawn rate: 100

### 2. Без GUI (headless)

```bash
locust -f locustfile.py \
    --host=http://localhost:8081 \
    --headless \
    --users 10000 \
    --spawn-rate 100 \
    --run-time 10m \
    --html report.html
```

### 3. Распределённый запуск (несколько машин)

**Master (основная машина):**
```bash
locust -f locustfile.py \
    --master \
    --host=http://localhost:8081 \
    --expect-workers 3
```

**Workers (дополнительные машины):**
```bash
locust -f locustfile.py \
    --worker \
    --master-host=<master-ip>
```

Это позволяет создать **100K+ пользователей** с нескольких машин.

## Тесты

| Файл | Описание |
|------|----------|
| `locustfile.py` | Базовый тест с реалистичным think time |
| `locust-extreme.py` | Экстремальный тест без think time, короткие таймауты |

## Параметры командной строки

| Параметр | Описание | Пример |
|----------|----------|--------|
| `-f` | Файл с тестом | `-f locustfile.py` |
| `--host` | URL сервера | `--host=http://localhost:8081` |
| `--headless` | Без Web UI | `--headless` |
| `--users` | Количество пользователей | `--users 50000` |
| `--spawn-rate` | Скорость создания пользователей/сек | `--spawn-rate 100` |
| `--run-time` | Время теста | `--run-time 15m` |
| `--html` | HTML отчёт | `--html report.html` |
| `--csv` | CSV статистика | `--csv results` |
| `--master` | Режим master | `--master` |
| `--worker` | Режим worker | `--worker` |
| `--master-host` | Адрес master | `--master-host=192.168.1.100` |

## Примеры запуска

### Нагрузочный тест (10K пользователей, 15 минут)

```bash
locust -f locustfile.py \
    --host=http://localhost:8081 \
    --headless \
    --users 10000 \
    --spawn-rate 50 \
    --run-time 15m \
    --html load_test_report.html
```

### Стресс-тест (50K пользователей, без таймаута)

```bash
locust -f locust-extreme.py \
    --host=http://localhost:8081 \
    --headless \
    --users 50000 \
    --spawn-rate 200 \
    --run-time 30m \
    --html stress_test_report.html
```

### Распределённый тест (100K пользователей, 3 машины)

**Master:**
```bash
locust -f locustfile.py \
    --master \
    --expect-workers 2 \
    --host=http://192.168.1.50:8081
```

**Workers (на двух других машинах):**
```bash
locust -f locustfile.py \
    --worker \
    --master-host=192.168.1.50
```

## Web UI

При запуске без `--headless`, Locust открывает Web UI на http://localhost:8089:

- **Statistics** — статистика в реальном времени
- **Charts** — графики RPS, latency, пользователей
- **Failures** — список ошибок
- **Download Data** — экспорт CSV/HTML

## Метрики

Locust автоматически собирает:

| Метрика | Описание |
|---------|----------|
| Type | Тип запроса (GET/POST) |
| Name | Имя endpoint'а |
| Requests | Количество запросов |
| Fails | Количество ошибок |
| Median | Медиана latency (ms) |
| 90% | 90-й перцентиль |
| 95% | 95-й перцентиль |
| 99% | 99-й перцентиль |
| Average | Средняя latency |
| Min/Max | Мин/макс latency |
| RPS | Запросов в секунду |

## Интеграция с CI/CD

```bash
# Возвращает ненулевой код при ошибках > 1%
locust -f locustfile.py \
    --headless \
    --users 1000 \
    --run-time 5m \
    --exit-code-on-error 1
```
