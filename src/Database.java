import java.sql.*;

// JDBC connection helper
public class Database {
    private static final String URL = "jdbc:sqlite:lostfound.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void init() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS items ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, name TEXT, description TEXT,"
                + "location TEXT, date TEXT, reporter TEXT, contact TEXT, status TEXT,"
                + "hidden_question TEXT, hidden_answer TEXT)";
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
            // Migration: add the new columns if the database was created by the older version
            for (String col : new String[]{"hidden_question", "hidden_answer"}) {
                try { s.execute("ALTER TABLE items ADD COLUMN " + col + " TEXT"); }
                catch (SQLException ignored) { /* column already exists */ }
            }
        }
    }
}
