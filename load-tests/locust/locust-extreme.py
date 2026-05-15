"""
locust-extreme.py — Экстремальный стресс-тест

Отличия от базового:
    - Нет think time
    - Короткие таймауты (5 сек)
    - Агрессивные retry при ошибках

Запуск:
    locust -f locust-extreme.py --host=http://localhost:8081
"""

import random
from locust import HttpUser, task, events
from locust.runners import MasterRunner, WorkerRunner


TEST_USER_IDS = list(range(1, 21))
TEST_GROUP_CHAT_ID = 1


class ExtremeUser(HttpUser):
    """Агрессивный пользователь для стресс-теста"""
    
    # Минимальный wait time
    wait_time = lambda self: 0.01
    
    # Короткий таймаут
    timeout = 5
    
    def on_start(self):
        self.user_id = random.choice(TEST_USER_IDS)
    
    @task(35)
    def get_chat_list(self):
        with self.client.get(
            f"/chats/{self.user_id}",
            timeout=self.timeout,
            catch_response=True,
            name="chat_list"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(25)
    def get_messages(self):
        with self.client.get(
            f"/chats/{TEST_GROUP_CHAT_ID}/messages?userId={self.user_id}",
            timeout=self.timeout,
            catch_response=True,
            name="messages"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(20)
    def search_users(self):
        letters = ['a', 'e', 'i', 'o', 'u', 't', 's', 'r', 'n', 'l']
        query = random.choice(letters)
        
        with self.client.get(
            f"/users/search?q={query}&selfId={self.user_id}",
            timeout=self.timeout,
            catch_response=True,
            name="search_users"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(12)
    def get_presence(self):
        ids = ",".join(map(str, TEST_USER_IDS[:10]))
        
        with self.client.get(
            f"/users/presence?ids={ids}",
            timeout=self.timeout,
            catch_response=True,
            name="presence"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(8)
    def global_search(self):
        queries = ['test', 'user', 'message', 'chat', 'group']
        query = random.choice(queries)
        
        with self.client.get(
            f"/search/global?q={query}&userId={self.user_id}",
            timeout=self.timeout,
            catch_response=True,
            name="global_search"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    if isinstance(environment.runner, MasterRunner):
        print("\n" + "=" * 60)
        print("LOCUST ЭКСТРЕМАЛЬНЫЙ ТЕСТ - MASTER")
        print("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("\n" + "=" * 60)
    print("ТЕСТ ЗАВЕРШЁН")
    print("=" * 60)
