public class User {
    private final String username;
    private final String password;
    private final boolean isAdmin;

    public User(String username, String password, boolean isAdmin) {
        this.username = username;
        this.password = password;
        this.isAdmin = isAdmin;
    }

    public String getUsername() {
        return username;
    }

    public boolean checkPassword(String inputPassword) {
        return password.equals(inputPassword);
    }

    public boolean isAdmin() {
        return isAdmin;
    }
}