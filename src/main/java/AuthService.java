import java.util.HashMap;
import java.util.Map;

public class AuthService {
    private final Map<String, User> users;

    public AuthService() {
        users = new HashMap<>();
        // Предзаполненные пользователи (админ по умолчанию)
        users.put("admin", new User("admin", "admin123", true));
    }

    public User authenticate(String username, String password) {
        User user = users.get(username);
        if (user != null && user.checkPassword(password)) {
            return user;
        }
        return null;
    }

    public boolean register(String username, String password, boolean isAdmin, User creator) {
        if (users.containsKey(username)) {
            return false; // Пользователь уже существует
        }
        if (isAdmin && (creator == null || !creator.isAdmin())) {
            return false; // Только админ может создавать админов
        }
        users.put(username, new User(username, password, isAdmin));
        return true;
    }
}