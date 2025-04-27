import java.sql.*;
import java.util.Scanner;
import javax.mail.MessagingException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.jsmpp.bean.*;

public class OTPApp {
    private static final AuthService authService = new AuthService();
    private static byte[] secretKey;

    // Настройки SMTP
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;
    private static final String EMAIL_USERNAME = "your.email@gmail.com";
    private static final String EMAIL_PASSWORD = "yourpassword";
    private static final boolean USE_TLS = true;

    // Настройки SMPP
    private static final String SMPP_HOST = "smpp.example.com";
    private static final int SMPP_PORT = 2775;
    private static final String SMPP_SYSTEM_ID = "your_smpp_login";
    private static final String SMPP_PASSWORD = "your_smpp_password";
    private static final String SMPP_SOURCE_ADDR = "OTPService";

    // Настройки Telegram
    private static final String TELEGRAM_BOT_TOKEN = "ваш_bot_token";
    private static final String TELEGRAM_BOT_USERNAME = "ваш_bot_username";

    private static EmailService emailService;
    private static SmppService smppService;
    private static TelegramService telegramService;

    public static void main(String[] args) {
        try {
            // Инициализация сервисов
            DatabaseManager.initializeDatabase();
            emailService = new EmailService(SMTP_HOST, SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, USE_TLS);
            smppService = new SmppService(
                    SMPP_HOST, SMPP_PORT, SMPP_SYSTEM_ID, SMPP_PASSWORD,
                    "", TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN,
                    SMPP_SOURCE_ADDR);
            telegramService = new TelegramService(TELEGRAM_BOT_TOKEN, TELEGRAM_BOT_USERNAME);

            Scanner scanner = new Scanner(System.in);
            User currentUser = null;

            // Очистка устаревших OTP
            OTPService.cleanupExpiredOTPs();

            // Главное меню (вход/регистрация)
            while (currentUser == null) {
                System.out.println("=== Меню ===");
                System.out.println("1. Вход");
                System.out.println("2. Регистрация");
                System.out.print("Выберите действие: ");

                try {
                    int action = Integer.parseInt(scanner.nextLine());

                    switch (action) {
                        case 1:
                            currentUser = login(scanner);
                            break;
                        case 2:
                            registerUser(scanner, null);
                            break;
                        default:
                            System.out.println("❌ Неверный выбор!");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("❌ Введите число!");
                }
            }

            // Инициализация TOTP
            TOTPGenerator otpGenerator = initTOTP(scanner, currentUser);

            // Главное меню
            while (true) {
                if (currentUser.isAdmin()) {
                    showAdminMenu(scanner, otpGenerator, currentUser);
                } else {
                    showUserMenu(scanner, otpGenerator, currentUser);
                }
            }

        } catch (Exception e) {
            System.err.println("🚨 Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static User login(Scanner scanner) throws SQLException {
        System.out.print("Логин: ");
        String username = scanner.nextLine();
        System.out.print("Пароль: ");
        String password = scanner.nextLine();

        User user = authService.authenticate(username, password);
        if (user == null) {
            System.out.println("❌ Неверный логин или пароль!");
            return null;
        }
        System.out.println("✅ Успешный вход! Здравствуйте, " + user.getUsername() + "!");
        return user;
    }

    private static void registerUser(Scanner scanner, User creator) throws SQLException {
        System.out.print("Логин: ");
        String username = scanner.nextLine();
        System.out.print("Пароль: ");
        String password = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Телефон (79123456789): ");
        String phone = scanner.nextLine();
        System.out.print("Telegram Chat ID (необязательно): ");
        String telegramChatId = scanner.nextLine();

        if (!validatePhoneNumber(phone)) {
            System.out.println("❌ Неверный формат телефона!");
            return;
        }

        boolean isAdmin = false;
        if (creator != null && creator.isAdmin()) {
            System.out.print("Сделать администратором? (y/n): ");
            isAdmin = scanner.nextLine().equalsIgnoreCase("y");
        }

        boolean success = authService.register(username, password, email, phone, telegramChatId, isAdmin, creator);
        if (success) {
            System.out.println("✅ Пользователь " + username + " зарегистрирован!");
        } else {
            System.out.println("❌ Ошибка регистрации (логин занят или нет прав)");
        }
    }

    private static boolean validatePhoneNumber(String phone) {
        return phone.matches("^7\\d{10}$");
    }

    private static TOTPGenerator initTOTP(Scanner scanner, User user) throws SQLException {
        if (user.isAdmin()) {
            System.out.println("1. Сгенерировать новый секретный ключ");
            System.out.println("2. Ввести существующий ключ");
            System.out.print("Выберите действие: ");

            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) {
                secretKey = TOTPGenerator.generateSecretKey();
                OTPStorage.saveSecretKey(user.getId(), secretKey);
                System.out.println("🔒 Ключ сохранён в БД: " + TOTPGenerator.bytesToBase32(secretKey));
            } else {
                System.out.print("Введите ключ (Base32): ");
                String base32Key = scanner.nextLine();
                secretKey = TOTPGenerator.base32ToBytes(base32Key);
                OTPStorage.saveSecretKey(user.getId(), secretKey);
            }
        } else {
            secretKey = OTPStorage.getSecretKey(user.getId());
            if (secretKey == null) {
                secretKey = TOTPGenerator.generateSecretKey();
                OTPStorage.saveSecretKey(user.getId(), secretKey);
            }
        }
        return new TOTPGenerator(secretKey);
    }

    private static void showUserMenu(Scanner scanner, TOTPGenerator otpGenerator, User user) {
        while (true) {
            System.out.println("\n=== Меню пользователя ===");
            System.out.println("1. Сгенерировать OTP");
            System.out.println("2. Проверить OTP");
            System.out.println("3. Отправить OTP на email");
            System.out.println("4. Отправить OTP по SMS");
            System.out.println("5. Отправить OTP в Telegram");
            System.out.println("6. Привязать Telegram аккаунт");
            System.out.println("7. Выход");
            System.out.print("Выберите действие: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());
                String otp;

                switch (choice) {
                    case 1:
                        otp = otpGenerator.generateAndSaveTOTP(user.getId());
                        System.out.println("🔄 OTP: " + otp);
                        break;
                    case 2:
                        System.out.print("Введите OTP: ");
                        String code = scanner.nextLine();
                        boolean isValid = otpGenerator.validateAndMarkUsed(user.getId(), code);
                        System.out.println(isValid ? "✅ Верно!" : "❌ Неверно!");
                        break;
                    case 3:
                        otp = otpGenerator.generateAndSaveTOTP(user.getId());
                        try {
                            emailService.sendEmail(user.getEmail(), "Ваш OTP код",
                                    "Ваш одноразовый код: " + otp + "\nДействителен 5 минут");
                            System.out.println("✉️ OTP отправлен на " + user.getEmail());
                        } catch (MessagingException e) {
                            System.out.println("❌ Ошибка отправки: " + e.getMessage());
                        }
                        break;
                    case 4:
                        otp = otpGenerator.generateAndSaveTOTP(user.getId());
                        try {
                            smppService.sendSms(user.getPhone(),
                                    "Ваш OTP код: " + otp + "\nДействителен 5 минут");
                            System.out.println("📱 OTP отправлен на номер " + user.getPhone());
                        } catch (Exception e) {
                            System.out.println("❌ Ошибка отправки SMS: " + e.getMessage());
                        }
                        break;
                    case 5:
                        if (user.getTelegramChatId() == null || user.getTelegramChatId().isEmpty()) {
                            System.out.println("❌ Telegram аккаунт не привязан");
                            break;
                        }
                        otp = otpGenerator.generateAndSaveTOTP(user.getId());
                        try {
                            telegramService.sendOTP(user.getTelegramChatId(), otp);
                            System.out.println("📨 OTP отправлен в Telegram");
                        } catch (TelegramApiException e) {
                            System.out.println("❌ Ошибка отправки в Telegram: " + e.getMessage());
                        }
                        break;
                    case 6:
                        bindTelegramAccount(scanner, user.getId());
                        break;
                    case 7:
                        System.exit(0);
                    default:
                        System.out.println("❌ Неверный выбор!");
                }
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
            }
        }
    }

    private static void bindTelegramAccount(Scanner scanner, int userId) throws SQLException {
        System.out.print("Введите ваш Telegram Chat ID: ");
        String chatId = scanner.nextLine();

        String sql = "UPDATE users SET telegram_chat_id = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
            System.out.println("✅ Telegram аккаунт привязан");
        }
    }

    private static void showAdminMenu(Scanner scanner, TOTPGenerator otpGenerator, User admin) {
        while (true) {
            System.out.println("\n=== Меню администратора ===");
            System.out.println("1. Сгенерировать OTP");
            System.out.println("2. Проверить OTP");
            System.out.println("3. Отправить OTP на email");
            System.out.println("4. Отправить OTP по SMS");
            System.out.println("5. Отправить OTP в Telegram");
            System.out.println("6. Показать текущий ключ");
            System.out.println("7. Изменить ключ");
            System.out.println("8. Зарегистрировать пользователя");
            System.out.println("9. Показать историю OTP");
            System.out.println("10. Выход");
            System.out.print("Выберите действие: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());
                String otp;

                switch (choice) {
                    case 1:
                        otp = otpGenerator.generateAndSaveTOTP(admin.getId());
                        System.out.println("🔄 OTP: " + otp);
                        break;
                    case 2:
                        System.out.print("Введите OTP: ");
                        String code = scanner.nextLine();
                        boolean isValid = otpGenerator.validateAndMarkUsed(admin.getId(), code);
                        System.out.println(isValid ? "✅ Верно!" : "❌ Неверно!");
                        break;
                    case 3:
                        otp = otpGenerator.generateAndSaveTOTP(admin.getId());
                        try {
                            emailService.sendEmail(admin.getEmail(), "Ваш OTP код",
                                    "Ваш одноразовый код: " + otp + "\nДействителен 5 минут");
                            System.out.println("✉️ OTP отправлен на " + admin.getEmail());
                        } catch (MessagingException e) {
                            System.out.println("❌ Ошибка отправки: " + e.getMessage());
                        }
                        break;
                    case 4:
                        otp = otpGenerator.generateAndSaveTOTP(admin.getId());
                        try {
                            smppService.sendSms(admin.getPhone(),
                                    "Ваш OTP код: " + otp + "\nДействителен 5 минут");
                            System.out.println("📱 OTP отправлен на номер " + admin.getPhone());
                        } catch (Exception e) {
                            System.out.println("❌ Ошибка отправки SMS: " + e.getMessage());
                        }
                        break;
                    case 5:
                        if (admin.getTelegramChatId() == null || admin.getTelegramChatId().isEmpty()) {
                            System.out.println("❌ Telegram аккаунт не привязан");
                            break;
                        }
                        otp = otpGenerator.generateAndSaveTOTP(admin.getId());
                        try {
                            telegramService.sendOTP(admin.getTelegramChatId(), otp);
                            System.out.println("📨 OTP отправлен в Telegram");
                        } catch (TelegramApiException e) {
                            System.out.println("❌ Ошибка отправки в Telegram: " + e.getMessage());
                        }
                        break;
                    case 6:
                        System.out.println("🔑 Текущий ключ: " + TOTPGenerator.bytesToBase32(secretKey));
                        break;
                    case 7:
                        System.out.print("Введите новый ключ (Base32): ");
                        String newKey = scanner.nextLine();
                        secretKey = TOTPGenerator.base32ToBytes(newKey);
                        OTPStorage.saveSecretKey(admin.getId(), secretKey);
                        System.out.println("🔑 Ключ изменён!");
                        break;
                    case 8:
                        registerUser(scanner, admin);
                        break;
                    case 9:
                        showOTPHistory();
                        break;
                    case 10:
                        System.exit(0);
                    default:
                        System.out.println("❌ Неверный выбор!");
                }
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
            }
        }
    }

    private static void showOTPHistory() {
        try {
            String sql = "SELECT u.username, o.code, o.generation_time, o.is_used " +
                    "FROM otp_codes o " +
                    "JOIN users u ON o.user_id = u.id " +
                    "ORDER BY o.generation_time DESC LIMIT 10";

            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                System.out.println("\n=== Последние 10 OTP-кодов ===");
                System.out.printf("%-15s %-10s %-25s %-10s%n",
                        "Пользователь", "Код", "Время генерации", "Использован");

                while (rs.next()) {
                    System.out.printf("%-15s %-10s %-25s %-10s%n",
                            rs.getString("username"),
                            rs.getString("code"),
                            rs.getTimestamp("generation_time"),
                            rs.getBoolean("is_used") ? "Да" : "Нет");
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка при получении истории: " + e.getMessage());
        }
    }
}