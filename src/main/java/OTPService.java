import java.sql.*;
import java.time.LocalDateTime;

public class OTPService {
    public static void saveOTP(int userId, String code) throws SQLException {
        String sql = "INSERT INTO otp_codes (user_id, code) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, code);
            stmt.executeUpdate();
        }
    }

    public static boolean validateOTP(int userId, String code) throws SQLException {
        String sql = "UPDATE otp_codes SET is_used = TRUE " +
                "WHERE user_id = ? AND code = ? AND is_used = FALSE " +
                "AND generation_time > (NOW() - INTERVAL '5 minutes') " +
                "RETURNING id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, code);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    public static void cleanupExpiredOTPs() throws SQLException {
        String sql = "DELETE FROM otp_codes WHERE generation_time < (NOW() - INTERVAL '1 day')";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
}