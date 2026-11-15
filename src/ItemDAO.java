import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// JDBC CRUD using PreparedStatement (prevents SQL injection)
public class ItemDAO implements Repository<Item> {

    @Override
    public void save(Item i) throws SQLException {
        String sql = "INSERT INTO items(type,name,description,location,date,reporter,contact,status,hidden_question,hidden_answer) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, i.getType());
            p.setString(2, i.getName());
            p.setString(3, i.getDescription());
            p.setString(4, i.getLocation());
            p.setString(5, i.getDate());
            p.setString(6, i.getReporter());
            p.setString(7, i.getContact());
            p.setString(8, i.getStatus());
            p.setString(9, i.getHiddenQuestion());
            p.setString(10, i.getHiddenAnswer());
            p.executeUpdate();
        }
    }

    @Override
    public List<Item> findAll() throws SQLException {
        List<Item> list = new ArrayList<>();   // COLLECTIONS
        try (Connection c = Database.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM items ORDER BY id DESC")) {
            while (r.next()) {
                String n = r.getString("name"), d = r.getString("description"), l = r.getString("location"),
                       dt = r.getString("date"), rp = r.getString("reporter"), ct = r.getString("contact");
                Item item = r.getString("type").equals("LOST")
                        ? new LostItem(n, d, l, dt, rp, ct)    // POLYMORPHISM: Item reference, subclass object
                        : new FoundItem(n, d, l, dt, rp, ct);
                item.setId(r.getInt("id"));
                item.setStatus(r.getString("status"));
                item.setHidden(r.getString("hidden_question"), r.getString("hidden_answer"));
                list.add(item);
            }
        }
        return list;
    }

    public void updateStatus(int id, String status) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE items SET status=? WHERE id=?")) {
            p.setString(1, status);
            p.setInt(2, id);
            p.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("DELETE FROM items WHERE id=?")) {
            p.setInt(1, id);
            p.executeUpdate();
        }
    }
}
