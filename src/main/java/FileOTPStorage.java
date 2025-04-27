import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileOTPStorage {
    public static final String OTP_LOG_DIR = "otp_logs";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void saveOTP(int userId, String otpCode) {
        try {
            // Создаем директорию если не существует
            new File(OTP_LOG_DIR).mkdirs();

            // Формируем имя файла
            String filename = String.format("%s/user_%d_%s.txt",
                    OTP_LOG_DIR,
                    userId,
                    LocalDateTime.now().format(DATE_FORMATTER));

            // Записываем OTP в файл
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.printf("[%s] Generated OTP for user %d: %s%n",
                        LocalDateTime.now(),
                        userId,
                        otpCode);
            }
        } catch (IOException e) {
            System.err.println("Error writing in OTP file " + e.getMessage());
        }
    }

    public static void logOTPValidation(int userId, String otpCode, boolean isValid) {
        try {
            String filename = OTP_LOG_DIR + "/otp_validations.log";
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) {
                writer.printf("[%s] User %d attempted validation with OTP %s: %s%n",
                        LocalDateTime.now(),
                        userId,
                        otpCode,
                        isValid ? "SUCCESS" : "FAILURE");
            }
        } catch (IOException e) {
            System.err.println("Validation log writing error : " + e.getMessage());
        }
    }

    public static void exportLogsToZip(String zipFileName) throws IOException {
        File logDir = new File(OTP_LOG_DIR);
        File[] logFiles = logDir.listFiles();

        if (logFiles == null || logFiles.length == 0) {
            throw new IOException("No logs for export");
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(
                new FileOutputStream(zipFileName))) {

            for (File file : logFiles) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zipOut.putNextEntry(zipEntry);

                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zipOut.write(bytes, 0, length);
                    }
                }
            }
        }
    }
}