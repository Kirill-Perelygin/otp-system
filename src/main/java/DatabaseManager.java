import java.sql.*;

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

            // Проверяем существование базы данных
            ResultSet rs = conn.getMetaData().getCatalogs();
            boolean dbExists = false;
            while (rs.next()) {
                if (DB_NAME.equals(rs.getString(1))) {
                    dbExists = true;
                    break;
                }
            }
            rs.close();

            if (!dbExists) {
                stmt.executeUpdate("CREATE DATABASE " + DB_NAME);
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        String connectionUrl = "jdbc:postgresql://localhost:5432/" + DB_NAME;
        return DriverManager.getConnection(connectionUrl, DB_USER, DB_PASSWORD);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Создаем схему public если не существует
            stmt.execute("CREATE SCHEMA IF NOT EXISTS public");

            // Создаем таблицы
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id SERIAL PRIMARY KEY," +
                    "username VARCHAR(50) UNIQUE NOT NULL," +
                    "password VARCHAR(100) NOT NULL," +
                    "is_admin BOOLEAN DEFAULT FALSE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS secrets (" +
                    "user_id INTEGER PRIMARY KEY REFERENCES users(id)," +
                    "secret_key BYTEA NOT NULL)");

            // Добавляем администратора если не существует
            stmt.execute("INSERT INTO users (username, password, is_admin) " +
                    "SELECT 'admin', 'admin123', TRUE " +
                    "WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin')");

            stmt.execute("CREATE TABLE IF NOT EXISTS otp_codes (" +
                    "id SERIAL PRIMARY KEY," +
                    "user_id INTEGER REFERENCES users(id)," +
                    "code VARCHAR(10) NOT NULL," +
                    "generation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "is_used BOOLEAN DEFAULT FALSE)");

        } catch (SQLException e) {
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}