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
