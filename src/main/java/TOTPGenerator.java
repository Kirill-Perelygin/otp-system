import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

public class TOTPGenerator {
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP = 30;
    private static final int CODE_DIGITS = 6;

    private final byte[] secretKey;

    public TOTPGenerator(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public static byte[] generateSecretKey() {
        byte[] key = new byte[20];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public String generateTOTP() {
        long timeCounter = System.currentTimeMillis() / 1000 / TIME_STEP;
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeCounter).array();

        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, HMAC_ALGORITHM);
            hmac.init(keySpec);
            byte[] hash = hmac.doFinal(timeBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) |
                    ((hash[offset + 1] & 0xFF) << 16) |
                    ((hash[offset + 2] & 0xFF) << 8) |
                    (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException("OTP generation error", e);
        }
    }

    public String generateAndSaveTOTP(int userId) throws SQLException {
        String otp = generateTOTP();
        OTPService.saveOTP(userId, otp);
        FileOTPStorage.saveOTP(userId, otp);
        return otp;
    }

    public boolean validateAndMarkUsed(int userId, String code) throws SQLException {
        boolean isValid = OTPService.validateOTP(userId, code);
        FileOTPStorage.logOTPValidation(userId, code, isValid);
        return isValid;
    }

    public boolean validateTOTP(String userCode) {
        return generateTOTP().equals(userCode);
    }

    public static String bytesToBase32(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] base32ToBytes(String base32) {
        return Base64.getDecoder().decode(base32);
    }
}