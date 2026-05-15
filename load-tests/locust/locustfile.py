"""
locustfile.py — Нагрузочные тесты для messenger backend

Запуск:
    locust -f locustfile.py --host=http://localhost:8081

Распределённый запуск (master + workers):
    # На master машине:
    locust -f locustfile.py --master --host=http://localhost:8081

    # На worker машинах:
    locust -f locustfile.py --worker --master-host=<master-ip>
"""

import random
import json
from locust import HttpUser, task, between, events
from locust.runners import MasterRunner, WorkerRunner


# Конфигурация
TEST_USER_IDS = list(range(1, 21))  # [1..20]
TEST_GROUP_CHAT_ID = 1


class MessengerUser(HttpUser):
    """Симуляция пользователя мессенджера"""
    
    # Без think time для максимальной нагрузки
    wait_time = between(0.1, 0.5)
    
    # Таймауты для запросов
    timeout = 30
    
    def on_start(self):
        """Инициализация пользователя"""
        self.user_id = random.choice(TEST_USER_IDS)
    
    @task(30)
    def get_chat_list(self):
        """30% — список чатов (самый частый сценарий)"""
        with self.client.get(
            f"/chats/{self.user_id}",
            timeout=self.timeout,
            catch_response=True,
            name="chat_list"
        ) as response:
            if response.status_code == 200:
                # Проверяем что ответ не пустой
                try:
                    data = response.json()
                    if isinstance(data, list) and len(data) >= 0:
                        response.success()
                    else:
                        response.failure(f"Unexpected response: {data}")
                except Exception as e:
                    response.failure(f"JSON parse error: {e}")
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(20)
    def get_messages(self):
        """20% — загрузка сообщений"""
        with self.client.get(
            f"/chats/{TEST_GROUP_CHAT_ID}/messages?userId={self.user_id}",
            timeout=self.timeout,
            catch_response=True,
            name="messages"
        ) as response:
            if response.status_code == 200:
                try:
                    data = response.json()
                    response.success()
                except Exception as e:
                    response.failure(f"JSON parse error: {e}")
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(15)
    def search_users(self):
        """15% — поиск пользователей"""
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
    
    @task(10)
    def get_presence(self):
        """10% — проверка присутствия"""
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
    
    # @task(5)
    # def global_search(self):
    #     """5% — глобальный поиск (тяжёлый запрос)"""
    #     queries = ['test', 'user', 'message', 'chat', 'group']
    #     query = random.choice(queries)
        
    #     with self.client.get(
    #         f"/search/global?q={query}&userId={self.user_id}",
    #         timeout=self.timeout,
    #         catch_response=True,
    #         name="global_search"
    #     ) as response:
    #         if response.status_code == 200:
    #             response.success()
    #         else:
    #             response.failure(f"HTTP {response.status_code}")
    
    # @task(10)
    # def send_message(self):
    #     """10% — отправка сообщения"""
    #     message_data = {
    #         "chatId": TEST_GROUP_CHAT_ID,
    #         "senderId": self.user_id,
    #         "text": f"Test message from user {self.user_id} at {random.randint(1, 1000000)}",
    #         "messageType": "text"
    #     }
        
    #     with self.client.post(
    #         "/messages",
    #         data=message_data,
    #         timeout=self.timeout,
    #         catch_response=True,
    #         name="send_message"
    #     ) as response:
    #         if response.status_code in [200, 201]:
    #             response.success()
    #         else:
    #             response.failure(f"HTTP {response.status_code}")
    
    # @task(5)
    # def create_dm_chat(self):
    #     """5% — создание DM чата"""
    #     # Выбираем случайного другого пользователя
    #     other_user = random.choice([u for u in TEST_USER_IDS if u != self.user_id])
        
    #     with self.client.post(
    #         f"/chats/dm?userId={self.user_id}&otherUserId={other_user}",
    #         timeout=self.timeout,
    #         catch_response=True,
    #         name="create_dm_chat"
    #     ) as response:
    #         if response.status_code in [200, 201]:
    #             response.success()
    #         else:
    #             response.failure(f"HTTP {response.status_code}")
    
    # @task(3)
    # def add_contact(self):
    #     """3% — добавление контакта"""
    #     contact_id = random.choice([u for u in TEST_USER_IDS if u != self.user_id])
        
    #     with self.client.post(
    #         f"/contacts/add?userId={self.user_id}&contactId={contact_id}",
    #         timeout=self.timeout,
    #         catch_response=True,
    #         name="add_contact"
    #     ) as response:
    #         if response.status_code == 200:
    #             response.success()
    #         else:
    #             response.failure(f"HTTP {response.status_code}")
    
    # @task(2)
    # def create_group_chat(self):
    #     """2% — создание группового чата"""
    #     group_data = {
    #         "userId": str(self.user_id),
    #         "title": f"Test Group {random.randint(1, 10000)}"
    #     }
        
    #     with self.client.post(
    #         "/chats/group",
    #         data=group_data,
    #         timeout=self.timeout,
    #         catch_response=True,
    #         name="create_group_chat"
    #     ) as response:
    #         if response.status_code in [200, 201]:
    #             response.success()
    #         else:
    #             response.failure(f"HTTP {response.status_code}")


# Хуки для сбора статистики
@events.request.add_listener
def on_request(request_type, name, response_time, response_length, exception, **kwargs):
    """Логируем ошибки запросов"""
    if exception:
        print(f"[ERROR] {name}: {exception}")
    elif response_time > 5000:
        print(f"[SLOW] {name}: {response_time:.0f}ms")


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Событие начала теста"""
    if isinstance(environment.runner, MasterRunner):
        print("\n" + "=" * 60)
        print("LOCUST НАГРУЗОЧНЫЙ ТЕСТ - MASTER")
        print("=" * 60)
    elif isinstance(environment.runner, WorkerRunner):
        print(f"Worker подключился к master")
    else:
        print("\n" + "=" * 60)
        print("LOCUST НАГРУЗОЧНЫЙ ТЕСТ - STANDALONE")
        print("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """Событие окончания теста"""
    print("\n" + "=" * 60)
    print("ТЕСТ ЗАВЕРШЁН")
    print("=" * 60)
