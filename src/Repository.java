import java.sql.SQLException;
import java.util.List;

// GENERICS: reusable data-access contract for any type T
public interface Repository<T> {
    void save(T t) throws SQLException;
    List<T> findAll() throws SQLException;
    void delete(int id) throws SQLException;
}
