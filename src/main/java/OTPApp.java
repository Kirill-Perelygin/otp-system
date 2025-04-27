import java.sql.*;
import java.util.Scanner;

public class OTPApp {
    private static final AuthService authService = new AuthService();
    private static byte[] secretKey;

    public static void main(String[] args) {
        try {
            // Инициализация базы данных
            DatabaseManager.initializeDatabase();
            Scanner scanner = new Scanner(System.in);
            User currentUser = null;

            // Очистка устаревших OTP-кодов
            try {
                OTPService.cleanupExpiredOTPs();
            } catch (SQLException e) {
                System.out.println("⚠️ Не удалось очистить старые OTP-коды: " + e.getMessage());
            }

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
            int currentUserId = getUserId(currentUser.getUsername());

            if (currentUserId == -1) {
                throw new RuntimeException("Пользователь не найден в базе данных");
            }

            // Главное меню (в зависимости от роли)
            while (true) {
                if (currentUser.isAdmin()) {
                    showAdminMenu(scanner, otpGenerator, currentUser, currentUserId);
                } else {
                    showUserMenu(scanner, otpGenerator, currentUserId);
                }
            }

        } catch (Exception e) {
            System.err.println("🚨 Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static User login(Scanner scanner) {
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

    private static void registerUser(Scanner scanner, User creator) {
        System.out.print("Логин: ");
        String username = scanner.nextLine();
        System.out.print("Пароль: ");
        String password = scanner.nextLine();

        boolean isAdmin = false;
        if (creator != null && creator.isAdmin()) {
            System.out.print("Сделать администратором? (y/n): ");
            isAdmin = scanner.nextLine().equalsIgnoreCase("y");
        }

        try {
            boolean success = authService.register(username, password, isAdmin, creator);
            if (success) {
                System.out.println("✅ Пользователь " + username + " зарегистрирован!");
            } else {
                System.out.println("❌ Ошибка регистрации (логин занят или нет прав)");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка при регистрации: " + e.getMessage());
        }
    }

    private static TOTPGenerator initTOTP(Scanner scanner, User user) throws SQLException {
        int userId = getUserId(user.getUsername());
        if (userId == -1) {
            throw new RuntimeException("Пользователь не найден в БД");
        }

        if (user.isAdmin()) {
            System.out.println("1. Сгенерировать новый секретный ключ");
            System.out.println("2. Ввести существующий ключ");
            System.out.print("Выберите действие: ");

            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) {
                secretKey = TOTPGenerator.generateSecretKey();
                OTPStorage.saveSecretKey(userId, secretKey);
                System.out.println("🔒 Ключ сохранён в БД: " + TOTPGenerator.bytesToBase32(secretKey));
            } else {
                System.out.print("Введите ключ (Base32): ");
                String base32Key = scanner.nextLine();
                secretKey = TOTPGenerator.base32ToBytes(base32Key);
                OTPStorage.saveSecretKey(userId, secretKey);
            }
        } else {
            secretKey = OTPStorage.getSecretKey(userId);
            if (secretKey == null) {
                secretKey = TOTPGenerator.generateSecretKey();
                OTPStorage.saveSecretKey(userId, secretKey);
            }
        }
        return new TOTPGenerator(secretKey);
    }

    private static int getUserId(String username) throws SQLException {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private static void showUserMenu(Scanner scanner, TOTPGenerator otpGenerator, int userId) {
        while (true) {
            System.out.println("\n=== Меню пользователя ===");
            System.out.println("1. Сгенерировать OTP");
            System.out.println("2. Проверить OTP");
            System.out.println("3. Выход");
            System.out.print("Выберите действие: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());

                switch (choice) {
                    case 1:
                        String otp = otpGenerator.generateAndSaveTOTP(userId);
                        System.out.println("🔄 OTP: " + otp);
                        break;
                    case 2:
                        System.out.print("Введите OTP: ");
                        String code = scanner.nextLine();
                        boolean isValid = otpGenerator.validateAndMarkUsed(userId, code);
                        System.out.println(isValid ? "✅ Верно!" : "❌ Неверно!");
                        break;
                    case 3:
                        System.exit(0);
                    default:
                        System.out.println("❌ Неверный выбор!");
                }
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
            }
        }
    }

    private static void showAdminMenu(Scanner scanner, TOTPGenerator otpGenerator, User admin, int adminId) {
        while (true) {
            System.out.println("\n=== Меню администратора ===");
            System.out.println("1. Сгенерировать OTP");
            System.out.println("2. Проверить OTP");
            System.out.println("3. Показать текущий ключ");
            System.out.println("4. Изменить ключ");
            System.out.println("5. Зарегистрировать пользователя");
            System.out.println("6. Показать историю OTP");
            System.out.println("7. Выход");
            System.out.print("Выберите действие: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());

                switch (choice) {
                    case 1:
                        String otp = otpGenerator.generateAndSaveTOTP(adminId);
                        System.out.println("🔄 OTP: " + otp);
                        break;
                    case 2:
                        System.out.print("Введите OTP: ");
                        String code = scanner.nextLine();
                        boolean isValid = otpGenerator.validateAndMarkUsed(adminId, code);
                        System.out.println(isValid ? "✅ Верно!" : "❌ Неверно!");
                        break;
                    case 3:
                        System.out.println("🔑 Текущий ключ: " + TOTPGenerator.bytesToBase32(secretKey));
                        break;
                    case 4:
                        System.out.print("Введите новый ключ (Base32): ");
                        String newKey = scanner.nextLine();
                        secretKey = TOTPGenerator.base32ToBytes(newKey);
                        OTPStorage.saveSecretKey(adminId, secretKey);
                        System.out.println("🔑 Ключ изменён!");
                        break;
                    case 5:
                        registerUser(scanner, admin);
                        break;
                    case 6:
                        showOTPHistory();
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