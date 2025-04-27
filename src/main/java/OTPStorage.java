import java.sql.*;

public class OTPStorage {
    public static byte[] getSecretKey(int userId) {
        String sql = "SELECT secret_key FROM secrets WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBytes("secret_key") : null;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void saveSecretKey(int userId, byte[] secretKey) {
        String sql = "INSERT INTO secrets (user_id, secret_key) VALUES (?, ?) " +
                "ON CONFLICT (user_id) DO UPDATE SET secret_key = EXCLUDED.secret_key";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setBytes(2, secretKey);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}