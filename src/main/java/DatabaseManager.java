import java.sql.*;
import java.io.File;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/otp_db?currentSchema=OTP";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "123";
    private static final String DB_NAME = "otp_db";

    static {
        try {
            Class.forName("org.postgresql.Driver");
            createDatabaseIfNotExists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private static void createDatabaseIfNotExists() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            new File(FileOTPStorage.OTP_LOG_DIR).mkdirs();

            ResultSet rs = conn.getMetaData().getCatalogs();
            boolean dbExists = false;
            while (rs.next()) {
                if ("otp_db".equals(rs.getString(1))) {
                    dbExists = true;
                    break;
                }
            }
            rs.close();

            if (!dbExists) {
                stmt.executeUpdate("CREATE DATABASE otp_db");
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        String connectionUrl = "jdbc:postgresql://localhost:5432/otp_db?currentSchema=public";
        return DriverManager.getConnection(connectionUrl, DB_USER, DB_PASSWORD);
    }

    public static void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id SERIAL PRIMARY KEY," +
                    "username VARCHAR(50) UNIQUE NOT NULL," +
                    "password VARCHAR(100) NOT NULL," +
                    "email VARCHAR(100)," +
                    "phone VARCHAR(20)," +
                    "telegram_chat_id VARCHAR(50)," +
                    "is_admin BOOLEAN DEFAULT FALSE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS secrets (" +
                    "user_id INTEGER PRIMARY KEY REFERENCES users(id)," +
                    "secret_key BYTEA NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS otp_codes (" +
                    "id SERIAL PRIMARY KEY," +
                    "user_id INTEGER REFERENCES users(id)," +
                    "code VARCHAR(10) NOT NULL," +
                    "generation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "is_used BOOLEAN DEFAULT FALSE)");

            // Добавляем администратора если не существует
            stmt.execute("INSERT INTO users (username, password, is_admin) " +
                    "SELECT 'admin', 'admin123', TRUE " +
                    "WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin')");
        }
    }
}