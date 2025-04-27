public class TestDriver {
    public static void main(String[] args) {
        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("Драйвер успешно загружен.");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("Не удалось загрузить драйвер.");
        }
    }
}

