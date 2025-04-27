# OTP System 🔐

![Java](https://img.shields.io/badge/Java-17+-orange)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Telegram](https://img.shields.io/badge/Telegram_Bot-API-green)
![License](https://img.shields.io/badge/License-MIT-brightgreen)

Многофункциональная система генерации и верификации одноразовых паролей (OTP) с поддержкой нескольких каналов доставки.

## 🌟 Особенности

- Генерация TOTP (Time-based OTP) по стандарту RFC 6238
- Поддержка нескольких каналов доставки:
  - 📧 Email (SMTP)
  - 📱 SMS (через SMPP)
  - 💬 Telegram бот
  - 📄 Файловый лог (для отладки)
- Двухуровневая система доступа:
  - 👨‍💻 Обычные пользователи
  - 👨‍💼 Администраторы
- Полный аудит операций:
  - База данных PostgreSQL
  - Файловые логи
  - История генераций/проверок

## 🛠 Технологии

| Компонент       | Технологии                          |
|-----------------|-------------------------------------|
| Бэкенд         | Java 17+, Maven                     |
| База данных    | PostgreSQL 15+                      |
| Шифрование     | HMAC-SHA1, Base32                   |
| SMTP           | JavaMail API                        |
| SMPP           | jSMPP                               |
| Telegram       | TelegramBots Java Library           |
| Логирование    | Файловая система + ZIP-архивация    |

## 🗂 Структура проекта
otp-system/
├── src/
│ ├── main/
│ │ ├── java/
│ │ │ ├── OTPApp.java # Главный класс приложения
│ │ │ ├── AuthService.java # Аутентификация и авторизация
│ │ │ ├── TOTPGenerator.java # Генерация и проверка OTP
│ │ │ ├── DatabaseManager.java # Работа с PostgreSQL
│ │ │ ├── EmailService.java # Отправка email
│ │ │ ├── SmppService.java # Отправка SMS
│ │ │ ├── TelegramService.java # Telegram бот
│ │ │ └── FileOTPStorage.java # Файловое логирование
│ │ └── resources/
│ │ └── application.conf # Конфигурация (пример)
├── pom.xml # Зависимости Maven
└── README.md

🖥 Использование
После запуска доступны два типа меню:

Меню пользователя 👨‍💻
Генерация OTP

Проверка OTP

Отправка кода на email/SMS/Telegram

Привязка Telegram аккаунта

Меню администратора 👨‍💼
Все функции пользователя +

Управление секретными ключами

Просмотр истории OTP

Экспорт логов

Регистрация новых пользователей

📊 Примеры работы
Генерация OTP:

🔄 OTP: 492716
Отправка в Telegram:

📨 OTP отправлен в Telegram
Проверка OTP:

Введите OTP: 492716
✅ Верно!
⚙️ Настройка сервисов

Telegram бот:

Создайте бота через @BotFather

Укажите токен в конфигурации

SMTP сервер:

Для Gmail включите "Less secure apps"

Или создайте App Password

SMPP сервер:

Настройте соединение с SMPP провайдером

Укажите параметры в конфигурации
