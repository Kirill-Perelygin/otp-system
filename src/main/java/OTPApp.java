import java.sql.*;
import java.util.Scanner;
import javax.mail.MessagingException;
import org.jsmpp.bean.*;
import org.jsmpp.session.*;

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

    private static EmailService emailService;
    private static SmppService smppService;

    public static void main(String[] args) {
        try {
            // Инициализация сервисов
            DatabaseManager.initializeDatabase();
            emailService = new EmailService(SMTP_HOST, SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, USE_TLS);
            smppService = new SmppService(
                    SMPP_HOST, SMPP_PORT, SMPP_SYSTEM_ID, SMPP_PASSWORD,
                    "", TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN,
                    SMPP_SOURCE_ADDR);

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
            int currentUserId = currentUser.getId();
            String userEmail = currentUser.getEmail();
            String userPhone = currentUser.getPhone();

            // Главное меню
            while (true) {
                if (currentUser.isAdmin()) {
                    showAdminMenu(scanner, otpGenerator, currentUser);
                } else {
                    showUserMenu(scanner, otpGenerator, currentUserId, userEmail, userPhone);
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

        if (!validatePhoneNumber(phone)) {
            System.out.println("❌ Неверный формат телефона!");
            return;
        }

        boolean isAdmin = false;
        if (creator != null && creator.isAdmin()) {
            System.out.print("Сделать администратором? (y/n): ");
            isAdmin = scanner.nextLine().equalsIgnoreCase("y");
        }

        boolean success = authService.register(username, password, email, phone, isAdmin, creator);
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

    private static void showUserMenu(Scanner scanner, TOTPGenerator otpGenerator,
                                     int userId, String userEmail, String userPhone) {
        while (true) {
            System.out.println("\n=== Меню пользователя ===");
            System.out.println("1. Сгенерировать OTP");
            System.out.println("2. Проверить OTP");
            System.out.println("3. Отправить OTP на email");
            System.out.println("4. Отправить OTP по SMS");
            System.out.println("5. Выход");
            System.out.print("Выберите действие: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());
                String otp;

                switch (choice) {
                    case 1:
                        otp = otpGenerator.generateAndSaveTOTP(userId);
                        System.out.println("🔄 OTP: " + otp);
                        break;
                    case 2:
                        System.out.print("Введите OTP: ");
                        String code = scanner.nextLine();
                        boolean isValid = otpGenerator.validateAndMarkUsed(userId, code);
                        System.out.println(isValid ? "✅ Верно!" : "❌ Неверно!");
                        break;
                    case 3:
                        otp = otpGenerator.generateAndSaveTOTP(userId);
                        try {
                            emailService.sendEmail(userEmail, "Ваш OTP код",
                                    "Ваш одноразовый код: " + otp + "\nДействителен 5 минут");
                            System.out.println("✉️ OTP отправлен на " + userEmail);
                        } catch (MessagingException e) {
                            System.out.println("❌ Ошибка отправки: " + e.getMessage());
                        }
                        break;
                    case 4:
                        otp = otpGenerator.generateAndSaveTOTP(userId);
                        try {
                            smppService.sendSms(userPhone,
                                    "Ваш OTP код: " + otp + "\nДействителен 5 минут");
                            System.out.println("📱 OTP отправлен на номер " + userPhone);
                        } catch (Exception e) {
                            System.out.println("❌ Ошибка отправки SMS: " + e.getMessage());
                        }
                        break;
                    case 5:
                        System.exit(0);
                    default:
                        System.out.println("❌ Неверный выбор!");
                }
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
            }
        }
    }

    private static void showAdminMenu(Scanner scanner, TOTPGenerator otpGenerator, User admin) {
        int adminId = admin.getId();
        String adminEmail = admin.getEmail();
        String adminPhone = admin.getPhone();

        while (true) {
            System.out.println("\n=== Меню администратора ===");
            System.out.println("1. Сгенерировать OTP");
            System.out.println("2. Проверить OTP");
            System.out.println("3. Отправить OTP на email");
            System.out.println("4. Отправить OTP по SMS");
            System.out.println("5. Показать текущий ключ");
            System.out.println("6. Изменить ключ");
            System.out.println("7. Зарегистрировать пользователя");
            System.out.println("8. Показать историю OTP");
            System.out.println("9. Выход");
            System.out.print("Выберите действие: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());
                String otp;

                switch (choice) {
                    case 1:
                        otp = otpGenerator.generateAndSaveTOTP(adminId);
                        System.out.println("🔄 OTP: " + otp);
                        break;
                    case 2:
                        System.out.print("Введите OTP: ");
                        String code = scanner.nextLine();
                        boolean isValid = otpGenerator.validateAndMarkUsed(adminId, code);
                        System.out.println(isValid ? "✅ Верно!" : "❌ Неверно!");
                        break;
                    case 3:
                        otp = otpGenerator.generateAndSaveTOTP(adminId);
                        try {
                            emailService.sendEmail(adminEmail, "Ваш OTP код",
                                    "Ваш одноразовый код: " + otp + "\nДействителен 5 минут");
                            System.out.println("✉️ OTP отправлен на " + adminEmail);
                        } catch (MessagingException e) {
                            System.out.println("❌ Ошибка отправки: " + e.getMessage());
                        }
                        break;
                    case 4:
                        otp = otpGenerator.generateAndSaveTOTP(adminId);
                        try {
                            smppService.sendSms(adminPhone,
                                    "Ваш OTP код: " + otp + "\nДействителен 5 минут");
                            System.out.println("📱 OTP отправлен на номер " + adminPhone);
                        } catch (Exception e) {
                            System.out.println("❌ Ошибка отправки SMS: " + e.getMessage());
                        }
                        break;
                    case 5:
                        System.out.println("🔑 Текущий ключ: " + TOTPGenerator.bytesToBase32(secretKey));
                        break;
                    case 6:
                        System.out.print("Введите новый ключ (Base32): ");
                        String newKey = scanner.nextLine();
                        secretKey = TOTPGenerator.base32ToBytes(newKey);
                        OTPStorage.saveSecretKey(adminId, secretKey);
                        System.out.println("🔑 Ключ изменён!");
                        break;
                    case 7:
                        registerUser(scanner, admin);
                        break;
                    case 8:
                        showOTPHistory();
                        break;
                    case 9:
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