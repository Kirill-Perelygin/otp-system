import java.sql.*;

public class AuthService {
    public User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, username, password, email, phone, telegram_chat_id, is_admin " +
                "FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && rs.getString("password").equals(password)) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("telegram_chat_id"),
                        rs.getBoolean("is_admin")
                );
            }
        }
        return null;
    }

    public boolean register(String username, String password,
                            String email, String phone, String telegramChatId,
                            boolean isAdmin, User creator) throws SQLException {
        if (creator != null && isAdmin && !creator.isAdmin()) {
            return false;
        }

        String sql = "INSERT INTO users (username, password, email, phone, telegram_chat_id, is_admin) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, email);
            stmt.setString(4, phone);
            stmt.setString(5, telegramChatId);
            stmt.setBoolean(6, isAdmin);

            return stmt.executeUpdate() > 0;
        }
    }

    public int getUserId(String username) throws SQLException {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }
}