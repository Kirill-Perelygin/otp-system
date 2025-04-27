import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class OTPApp {
    private static final AuthService authService = new AuthService();
    private static byte[] secretKey;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        User currentUser = null;

        // Главное меню (вход/регистрация)
        while (currentUser == null) {
            System.out.println("=== Меню ===");
            System.out.println("1. Вход");
            System.out.println("2. Регистрация");
            System.out.print("Выберите действие: ");
            int action = scanner.nextInt();
            scanner.nextLine();

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
        }

        // Инициализация TOTP
        TOTPGenerator otpGenerator = initTOTP(scanner, currentUser);

        // Главное меню (в зависимости от роли)
        while (true) {
            if (currentUser.isAdmin()) {
                showAdminMenu(scanner, otpGenerator, currentUser);
            } else {
                showUserMenu(scanner, otpGenerator);
            }
        }
    }

    // Аутентификация
    private static User login(Scanner scanner) {
        System.out.print("Логин: ");
        String username = scanner.nextLine();
        System.out.print("Пароль: ");
        String password = scanner.nextLine();

        User user = authService.authenticate(username, password);
        if (user == null) {
            System.out.println("❌ Неверный логин или пароль!");
        } else {
            System.out.println("✅ Успешный вход! Здравствуйте, " + user.getUsername() + "!");
        }
        return user;
    }

    // Регистрация
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

        boolean success = authService.register(username, password, isAdmin, creator);
        if (success) {
            System.out.println("✅ Пользователь " + username + " зарегистрирован!");
        } else {
            System.out.println("❌ Ошибка регистрации (логин занят или нет прав)");
        }
    }

    // Инициализация TOTP
    private static TOTPGenerator initTOTP(Scanner scanner, User user) {
        if (user.isAdmin()) {
            System.out.println("1. Сгенерировать новый секретный ключ");
            System.out.println("2. Ввести существующий ключ");
            System.out.print("Выберите действие: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {
                secretKey = TOTPGenerator.generateSecretKey();
                System.out.println("🔒 Ключ: " + TOTPGenerator.bytesToBase32(secretKey));
            } else {
                System.out.print("Введите ключ (Base32): ");
                String base32Key = scanner.nextLine();
                secretKey = TOTPGenerator.base32ToBytes(base32Key);
            }
        } else {
            secretKey = TOTPGenerator.generateSecretKey();
        }
        return new TOTPGenerator(secretKey);
    }

    // Меню пользователя
    private static void showUserMenu(Scanner scanner, TOTPGenerator otpGenerator) {
        System.out.println("\n=== Меню пользователя ===");
        System.out.println("1. Сгенерировать OTP");
        System.out.println("2. Проверить OTP");
        System.out.println("3. Выход");
        System.out.print("Выберите действие: ");

        int choice = scanner.nextInt();
        scanner.nextLine();

        switch (choice) {
            case 1:
                System.out.println("🔄 OTP: " + otpGenerator.generateTOTP());
                break;
            case 2:
                System.out.print("Введите OTP: ");
                String code = scanner.nextLine();
                System.out.println(otpGenerator.validateTOTP(code) ? "✅ Верно!" : "❌ Неверно!");
                break;
            case 3:
                System.exit(0);
            default:
                System.out.println("❌ Неверный выбор!");
        }
    }

    // Меню администратора
    private static void showAdminMenu(Scanner scanner, TOTPGenerator otpGenerator, User admin) {
        System.out.println("\n=== Меню администратора ===");
        System.out.println("1. Сгенерировать OTP");
        System.out.println("2. Проверить OTP");
        System.out.println("3. Показать текущий ключ");
        System.out.println("4. Изменить ключ");
        System.out.println("5. Зарегистрировать пользователя");
        System.out.println("6. Выход");
        System.out.print("Выберите действие: ");

        int choice = scanner.nextInt();
        scanner.nextLine();

        switch (choice) {
            case 1:
                System.out.println("🔄 OTP: " + otpGenerator.generateTOTP());
                break;
            case 2:
                System.out.print("Введите OTP: ");
                String code = scanner.nextLine();
                System.out.println(otpGenerator.validateTOTP(code) ? "✅ Верно!" : "❌ Неверно!");
                break;
            case 3:
                System.out.println("🔑 Текущий ключ: " + TOTPGenerator.bytesToBase32(secretKey));
                break;
            case 4:
                System.out.print("Введите новый ключ (Base32): ");
                String newKey = scanner.nextLine();
                secretKey = TOTPGenerator.base32ToBytes(newKey);
                System.out.println("🔑 Ключ изменен!");
                break;
            case 5:
                registerUser(scanner, admin);
                break;
            case 6:
                System.exit(0);
            default:
                System.out.println("❌ Неверный выбор!");
        }
    }
}