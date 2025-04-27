public class User {
    private final int id;
    private final String username;
    private final String password;
    private final String email;
    private final String phone;
    private final String telegramChatId;
    private final boolean isAdmin;

    public User(int id, String username, String password,
                String email, String phone, String telegramChatId,
                boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.telegramChatId = telegramChatId;
        this.isAdmin = isAdmin;
    }

    // Геттеры
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getTelegramChatId() { return telegramChatId; }
    public boolean isAdmin() { return isAdmin; }

    public boolean checkPassword(String inputPassword) {
        return password.equals(inputPassword);
    }

    public String getLogFilenamePrefix() {
        return "user_" + id + "_" + username.replaceAll("[^a-zA-Z0-9]", "_");
    }
}