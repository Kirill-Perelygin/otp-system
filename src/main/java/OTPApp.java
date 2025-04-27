import java.io.*;
import java.sql.*;
import java.util.Scanner;
import java.util.zip.*;
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
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            // Инициализация сервисов
            DatabaseManager.initializeDatabase();
            initializeServices();

            // Очистка устаревших OTP
            OTPService.cleanupExpiredOTPs();

            // Главное меню (вход/регистрация)
            User currentUser = authMenu();

            // Инициализация TOTP
            TOTPGenerator otpGenerator = initTOTP(scanner, currentUser);

            // Главное меню
            mainMenuLoop(currentUser, otpGenerator);

        } catch (Exception e) {
            System.err.println("🚨 Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    private static void initializeServices() {
        emailService = new EmailService(SMTP_HOST, SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, USE_TLS);
        smppService = new SmppService(
                SMPP_HOST, SMPP_PORT, SMPP_SYSTEM_ID, SMPP_PASSWORD,
                "", TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN,
                SMPP_SOURCE_ADDR);
        telegramService = new TelegramService(TELEGRAM_BOT_TOKEN, TELEGRAM_BOT_USERNAME);
    }

    private static User authMenu() throws SQLException {
        User currentUser = null;
        while (currentUser == null) {
            System.out.println("=== Меню ===");
            System.out.println("1. Вход");
            System.out.println("2. Регистрация");
            System.out.print("Выберите действие: ");

            try {
                int action = Integer.parseInt(scanner.nextLine());

                switch (action) {
                    case 1:
                        currentUser = login();
                        break;
                    case 2:
                        registerUser(null);
                        break;
                    default:
                        System.out.println("Wrong choice");
                }
            } catch (NumberFormatException e) {
                System.out.println("Enter number");
            }
        }
        return currentUser;
    }

    private static User login() throws SQLException {
        System.out.print("Login: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        User user = authService.authenticate(username, password);
        if (user == null) {
            System.out.println("Invalid username or password");
            return null;
        }
        System.out.println("Succes. Hello, " + user.getUsername() + "!");
        return user;
    }

    private static void registerUser(User creator) throws SQLException {
        System.out.print("Login: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Phone by mask (79123456789): ");
        String phone = scanner.nextLine();
        System.out.print("Telegram Chat ID (optional): ");
        String telegramChatId = scanner.nextLine();

        if (!validatePhoneNumber(phone)) {
            System.out.println("Invalid phone format");
            return;
        }

        boolean isAdmin = false;
        if (creator != null && creator.isAdmin()) {
            System.out.print("Give admin role: (y/n) ?");
            isAdmin = scanner.nextLine().equalsIgnoreCase("y");
        }

        boolean success = authService.register(username, password, email, phone, telegramChatId, isAdmin, creator);
        System.out.println(success ? "User " + username + " is successfully registered!"
                : "Register failed. The login has already been registered or user has no rights");
    }

    private static boolean validatePhoneNumber(String phone) {
        return phone.matches("^7\\d{10}$");
    }

    private static TOTPGenerator initTOTP(Scanner scanner, User user) throws SQLException {
        if (user.isAdmin()) {
            System.out.println("1. Generate new secret key");
            System.out.println("2. Enter secret key");
            System.out.print("Choose your option: ");

            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) {
                secretKey = TOTPGenerator.generateSecretKey();
                OTPStorage.saveSecretKey(user.getId(), secretKey);
                System.out.println("The key is stored in database " + TOTPGenerator.bytesToBase32(secretKey));
            } else {
                System.out.print("Enter key (Base32): ");
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

    private static void mainMenuLoop(User currentUser, TOTPGenerator otpGenerator) {
        while (true) {
            if (currentUser.isAdmin()) {
                showAdminMenu(currentUser, otpGenerator);
            } else {
                showUserMenu(currentUser, otpGenerator);
            }
        }
    }

    private static void showUserMenu(User user, TOTPGenerator otpGenerator) {
        System.out.println("\n=== User menu ===");
        System.out.println("1. Generate OTP");
        System.out.println("2. Check OTP");
        System.out.println("3. Sent OTP via email");
        System.out.println("4. Sent OTP via SMS");
        System.out.println("5. Sent OTP via Telegram");
        System.out.println("6. Connect Telegram account");
        System.out.println("7. Exit");
        System.out.print("Chose your option: ");

        try {
            int choice = Integer.parseInt(scanner.nextLine());
            String otp;

            switch (choice) {
                case 1:
                    otp = otpGenerator.generateAndSaveTOTP(user.getId());
                    System.out.println("🔄 OTP: " + otp);
                    break;
                case 2:
                    validateOTP(otpGenerator, user.getId());
                    break;
                case 3:
                    sendOtpByEmail(otpGenerator, user);
                    break;
                case 4:
                    sendOtpBySms(otpGenerator, user);
                    break;
                case 5:
                    sendOtpByTelegram(otpGenerator, user);
                    break;
                case 6:
                    bindTelegramAccount(user.getId());
                    break;
                case 7:
                    System.exit(0);
                default:
                    System.out.println("Invalid choice");
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private static void showAdminMenu(User admin, TOTPGenerator otpGenerator) {
        System.out.println("\n=== Admin's menu ===");
        System.out.println("1. Generate OTP");
        System.out.println("2. Check OTP");
        System.out.println("3. Sent OTP via email");
        System.out.println("4. Sent OTP via SMS");
        System.out.println("5. Sent OTP via Telegram");
        System.out.println("6. Show active key");
        System.out.println("7. Change key");
        System.out.println("8. Register user");
        System.out.println("9. Show OTP history");
        System.out.println("10. Show OTP logs");
        System.out.println("11. Export logs");
        System.out.println("12. Exit");
        System.out.print("Chose your option: ");

        try {
            int choice = Integer.parseInt(scanner.nextLine());
            String otp;

            switch (choice) {
                case 1:
                    otp = otpGenerator.generateAndSaveTOTP(admin.getId());
                    System.out.println("🔄 OTP: " + otp);
                    break;
                case 2:
                    validateOTP(otpGenerator, admin.getId());
                    break;
                case 3:
                    sendOtpByEmail(otpGenerator, admin);
                    break;
                case 4:
                    sendOtpBySms(otpGenerator, admin);
                    break;
                case 5:
                    sendOtpByTelegram(otpGenerator, admin);
                    break;
                case 6:
                    System.out.println("Active key " + TOTPGenerator.bytesToBase32(secretKey));
                    break;
                case 7:
                    updateSecretKey(admin.getId());
                    break;
                case 8:
                    registerUser(admin);
                    break;
                case 9:
                    showDatabaseOtpHistory();
                    break;
                case 10:
                    showFileLogsMenu();
                    break;
                case 11:
                    exportLogsToZip();
                    break;
                case 12:
                    System.exit(0);
                default:
                    System.out.println("Invalid choice");
            }
        } catch (Exception e) {
            System.out.println("Error " + e.getMessage());
        }
    }

    private static void validateOTP(TOTPGenerator otpGenerator, int userId) throws SQLException {
        System.out.print("Enter OTP: ");
        String code = scanner.nextLine();
        boolean isValid = otpGenerator.validateAndMarkUsed(userId, code);
        System.out.println(isValid ? "Correct!" : "Incorrect!");
    }

    private static void sendOtpByEmail(TOTPGenerator otpGenerator, User user) throws SQLException, MessagingException {
        String otp = otpGenerator.generateAndSaveTOTP(user.getId());
        emailService.sendEmail(user.getEmail(), "Your OTP code",
                "Your OTP code: " + otp + "\nIs valid for 5 minutes");
        System.out.println("OTP sent via " + user.getEmail());
    }

    private static void sendOtpBySms(TOTPGenerator otpGenerator, User user) throws Exception {
        String otp = otpGenerator.generateAndSaveTOTP(user.getId());
        smppService.sendSms(user.getPhone(),
                "Your OTP code: " + otp + "\nIs valid for 5 minutes");
        System.out.println("OTP sent via " + user.getPhone());
    }

    private static void sendOtpByTelegram(TOTPGenerator otpGenerator, User user) throws SQLException, TelegramApiException {
        if (user.getTelegramChatId() == null || user.getTelegramChatId().isEmpty()) {
            System.out.println("Telegram account is not linked");
            return;
        }
        String otp = otpGenerator.generateAndSaveTOTP(user.getId());
        telegramService.sendOTP(user.getTelegramChatId(), otp);
        System.out.println("OTP sent via " + user.getTelegramChatId());
    }

    private static void bindTelegramAccount(int userId) throws SQLException {
        System.out.print("Enter your telegram chat id: ");
        String chatId = scanner.nextLine();

        String sql = "UPDATE users SET telegram_chat_id = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, chatId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
            System.out.println("Telegram account is linked");
        }
    }

    private static void updateSecretKey(int userId) throws SQLException {
        System.out.print("Enter new key (Base32): ");
        String newKey = scanner.nextLine();
        secretKey = TOTPGenerator.base32ToBytes(newKey);
        OTPStorage.saveSecretKey(userId, secretKey);
        System.out.println("The key has been updated");
    }

    private static void showDatabaseOtpHistory() {
        try {
            String sql = "SELECT u.username, o.code, o.generation_time, o.is_used " +
                    "FROM otp_codes o " +
                    "JOIN users u ON o.user_id = u.id " +
                    "ORDER BY o.generation_time DESC LIMIT 10";

            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                System.out.println("\n=== Latest 10 OTP codes ===");
                System.out.printf("%-15s %-10s %-25s %-10s%n",
                        "User", "Code", "Generation time", "Is valid?");

                while (rs.next()) {
                    System.out.printf("%-15s %-10s %-25s %-10s%n",
                            rs.getString("username"),
                            rs.getString("code"),
                            rs.getTimestamp("generation_time"),
                            rs.getBoolean("is_used") ? "Yes" : "No");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting history " + e.getMessage());
        }
    }

    private static void showFileLogsMenu() {
        File logDir = new File(FileOTPStorage.OTP_LOG_DIR);
        File[] logFiles = logDir.listFiles(
                (dir, name) -> name.endsWith(".txt") || name.endsWith(".log"));

        if (logFiles == null || logFiles.length == 0) {
            System.out.println("Logs are not found");
            return;
        }

        System.out.println("\n=== Log files to explore ===");
        for (int i = 0; i < logFiles.length; i++) {
            System.out.printf("%d. %s (%d KB)%n",
                    i+1,
                    logFiles[i].getName(),
                    logFiles[i].length() / 1024);
        }

        System.out.print("Chose file you would like to explore (0 - cancel): ");
        try {
            int fileChoice = Integer.parseInt(scanner.nextLine());
            if (fileChoice > 0 && fileChoice <= logFiles.length) {
                printFileContents(logFiles[fileChoice-1]);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void printFileContents(File file) {
        System.out.println("\n=== File consists of " + file.getName() + " ===");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 100) {
                System.out.println(line);
                lineCount++;
            }
            if (line != null) {
                System.out.println("... (show first 100)");
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    private static void exportLogsToZip() {
        System.out.print("Enter zip archive name: ");
        String zipName = scanner.nextLine() + ".zip";

        try {
            FileOTPStorage.exportLogsToZip(zipName);
            System.out.println("Logs are successfully exported " + zipName);
        } catch (IOException e) {
            System.out.println("Error while exporting logs " + e.getMessage());
        }
    }
}