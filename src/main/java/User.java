public class User {
    private final int id;
    private final String username;
    private final String password;
    private final String email;
    private final String phone;
    private final boolean isAdmin;

    public User(int id, String username, String password,
                String email, String phone, boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.isAdmin = isAdmin;
    }

    // Геттеры
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public boolean isAdmin() { return isAdmin; }

    public boolean checkPassword(String inputPassword) {
        return password.equals(inputPassword);
    }
}