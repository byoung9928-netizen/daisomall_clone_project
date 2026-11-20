package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class StoreInventoryDAO {
    private static final String STOCK_FIELDS =
            "SELECT p.product_id, COALESCE(i.quantity, 0) AS quantity, COALESCE(i.version, -1) AS version, "
            + "COALESCE(i.is_carried, 0) AS is_carried, p.name, p.image_url, COALESCE(p.product_code, '') AS product_code ";
    private static final String SEARCH = " AND (LOCATE(?, %s) > 0 OR LOCATE(?, %s) > 0)";

    public enum State {
        ALL("전체"), NORMAL("정상 (6개 이상)"), LOW("부족 (1~5개)"), SOLD_OUT("품절 (0개)");

        public final String label;
        State(String label) { this.label = label; }

        public static State ofQuantity(int quantity) {
            return quantity == 0 ? SOLD_OUT : quantity <= 5 ? LOW : NORMAL;
        }

        private String predicate() {
            switch (this) {
                case NORMAL: return " AND i.quantity >= 6";
                case LOW: return " AND i.quantity BETWEEN 1 AND 5";
                case SOLD_OUT: return " AND i.quantity = 0";
                default: return "";
            }
        }
    }

    public enum Change {
        RECEIVE("입고", 1), ISSUE("출고", -1), DAMAGE("파손", -1),
        RETURN("반품 입고", 1), COUNT("재고 조사", 0);

        public final String label;
        private final int direction;
        Change(String label, int direction) {
            this.label = label;
            this.direction = direction;
        }

        public int after(int before, int amount) {
            if (direction != 0 && amount == 0) throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
            long next = direction == 0 ? amount : before + (long) amount * direction;
            if (next < 0 || next > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("변경 후 재고가 허용 범위를 벗어납니다.");
            }
            if (next == before) throw new IllegalArgumentException("변경 후 수량이 현재 재고와 같습니다.");
            return (int) next;
        }
    }

    public enum Assortment {
        REGISTER("취급 등록"), RESUME("취급 재개"), UNLIST("취급 해제");

        public final String label;
        Assortment(String label) { this.label = label; }
    }

    public List<Location> locations(Connection connection) throws SQLException {
        List<Location> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT store_id, store_name, city FROM store_locations ORDER BY store_id");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new Location(rows.getString("store_id"), rows.getString("store_name"),
                        rows.getString("city")));
            }
        }
        return result;
    }

    public List<Stock> inventory(Connection connection, String store, String keyword, State state, int limit, int offset)
            throws SQLException {
        String sql = STOCK_FIELDS + "FROM store_inventory i JOIN products p ON p.product_id = i.product_id "
                + "WHERE i.store_id = ? AND i.is_carried = 1" + state.predicate()
                + String.format(SEARCH, "p.name", "p.product_code") + " ORDER BY p.name, i.product_id LIMIT ? OFFSET ?";
        List<Stock> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPage(statement, store, keyword, limit, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readStock(rows));
            }
        }
        return result;
    }

    public List<Stock> assortment(Connection connection, String store, String keyword, int limit, int offset)
            throws SQLException {
        String sql = STOCK_FIELDS + "FROM products p LEFT JOIN store_inventory i "
                + "ON i.product_id = p.product_id AND i.store_id = ? WHERE 1 = 1"
                + String.format(SEARCH, "p.name", "p.product_code") + " ORDER BY p.name, p.product_id LIMIT ? OFFSET ?";
        List<Stock> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPage(statement, store, keyword, limit, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readStock(rows));
            }
        }
        return result;
    }

    public Summary summary(Connection connection, String store) throws SQLException {
        String sql = "SELECT COUNT(*) AS kinds, COALESCE(SUM(quantity), 0) AS units, "
                + "COUNT(CASE WHEN quantity BETWEEN 1 AND 5 THEN 1 END) AS low, "
                + "COUNT(CASE WHEN quantity = 0 THEN 1 END) AS sold_out "
                + "FROM store_inventory WHERE store_id = ? AND is_carried = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, store);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return new Summary(row.getLong("kinds"), row.getLong("units"),
                        row.getLong("low"), row.getLong("sold_out"));
            }
        }
    }

    public List<History> history(Connection connection, String store, String keyword, int limit, int offset)
            throws SQLException {
        String sql = "SELECT product_name, product_code, product_reference, change_type, before_quantity, "
                + "after_quantity, reason, administrator_id, changed_at FROM store_inventory_history WHERE store_id = ?"
                + String.format(SEARCH, "product_name", "product_code")
                + " ORDER BY changed_at DESC, history_id DESC LIMIT ? OFFSET ?";
        List<History> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPage(statement, store, keyword, limit, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new History(rows));
            }
        }
        return result;
    }

    public Integer quantity(Connection connection, String store, String product) throws SQLException {
        try {
            return quantity(connection, store, product, " AND is_carried = 1");
        } catch (SQLException error) {
            // Keep the existing customer lookup available while the migration is pending.
            if (error.getErrorCode() != 1054 || !"42S22".equals(error.getSQLState())) throw error;
            return quantity(connection, store, product, "");
        }
    }

    private Integer quantity(Connection connection, String store, String product, String statePredicate) throws SQLException {
        String sql = "SELECT quantity FROM store_inventory WHERE store_id = ? AND product_id = ?" + statePredicate;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, store);
            statement.setString(2, product);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("quantity") : null;
            }
        }
    }

    public Stock find(Connection connection, String store, String product, boolean lock) throws SQLException {
        String sql = STOCK_FIELDS + "FROM store_inventory i JOIN products p ON p.product_id = i.product_id "
                + "WHERE i.store_id = ? AND i.product_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, store);
            statement.setString(2, product);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readStock(rows) : null;
            }
        }
    }

    public void update(Connection connection, String store, Stock before, int after, Change change, String reason,
            String administrator) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE store_inventory "
                + "SET quantity = ?, version = version + 1 "
                + "WHERE store_id = ? AND product_id = ? AND version = ? AND is_carried = 1")) {
            statement.setInt(1, after);
            statement.setString(2, store);
            statement.setString(3, before.id);
            statement.setLong(4, before.version);
            if (statement.executeUpdate() != 1) throw new SQLException("Store inventory row could not be updated");
        }
        recordChange(connection, store, before, after, change.name(), reason, administrator);
    }

    public boolean lockStore(Connection connection, String store, String city) throws SQLException {
        String sql = "SELECT store_id FROM store_locations WHERE store_id = ? AND (? = '' OR city = ?) FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, store);
            statement.setString(2, city);
            statement.setString(3, city);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && store.equals(rows.getString("store_id"));
            }
        }
    }

    public Stock findProduct(Connection connection, String product) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT p.product_id, p.name, p.image_url, "
                + "COALESCE(p.product_code, '') AS product_code, 0 AS quantity, -1 AS version, 0 AS is_carried "
                + "FROM products p WHERE p.product_id = ? FOR UPDATE")) {
            statement.setString(1, product);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readStock(rows) : null;
            }
        }
    }

    public void changeAssortment(Connection connection, String store, Stock before, int quantity, Assortment action,
            String reason, String administrator) throws SQLException {
        String sql = action == Assortment.REGISTER
                ? "INSERT INTO store_inventory (quantity, is_carried, store_id, product_id) VALUES (?, ?, ?, ?)"
                : "UPDATE store_inventory SET quantity = ?, is_carried = ?, version = version + 1 "
                    + "WHERE store_id = ? AND product_id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setBoolean(2, action != Assortment.UNLIST);
            statement.setString(3, store);
            statement.setString(4, before.id);
            if (action != Assortment.REGISTER) statement.setLong(5, before.version);
            if (statement.executeUpdate() != 1) throw new SQLException("Store assortment row could not be changed");
        }
        recordChange(connection, store, before, quantity, action.name(), reason, administrator);
    }

    private void recordChange(Connection connection, String store, Stock before, int after, String type,
            String reason, String administrator) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO store_inventory_history "
                + "(store_id, product_id, product_reference, product_name, product_code, change_type, "
                + "before_quantity, after_quantity, reason, administrator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, store);
            statement.setString(2, before.id);
            statement.setString(3, before.id);
            statement.setString(4, before.name);
            statement.setString(5, before.code);
            statement.setString(6, type);
            statement.setInt(7, before.quantity);
            statement.setInt(8, after);
            statement.setString(9, reason);
            statement.setString(10, administrator);
            statement.executeUpdate();
        }
    }

    private static void bindPage(PreparedStatement statement, String store, String keyword, int limit, int offset)
            throws SQLException {
        statement.setString(1, store);
        statement.setString(2, keyword);
        statement.setString(3, keyword);
        statement.setInt(4, limit);
        statement.setInt(5, offset);
    }

    private static Stock readStock(ResultSet row) throws SQLException {
        return new Stock(row.getString("product_id"), row.getString("name"), row.getString("product_code"),
                row.getString("image_url"), row.getInt("quantity"), row.getLong("version"),
                row.getBoolean("is_carried"));
    }

    public static final class Location {
        public final String id;
        public final String name;
        public final String city;

        private Location(String id, String name, String city) {
            this.id = id;
            this.name = name;
            this.city = city;
        }
    }

    public static final class Summary {
        public final long kinds;
        public final long units;
        public final long low;
        public final long soldOut;

        private Summary(long kinds, long units, long low, long soldOut) {
            this.kinds = kinds;
            this.units = units;
            this.low = low;
            this.soldOut = soldOut;
        }
    }

    public static final class Stock {
        public final String id;
        public final String name;
        public final String code;
        public final String image;
        public final int quantity;
        public final long version;
        public final boolean carried;

        private Stock(String id, String name, String code, String image, int quantity, long version, boolean carried) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.image = image;
            this.quantity = quantity;
            this.version = version;
            this.carried = carried;
        }

        public String stateLabel() {
            return State.ofQuantity(quantity).label;
        }
    }

    public static final class History {
        public final String name;
        public final String code;
        public final String reference;
        public final String type;
        public final int before;
        public final int after;
        public final String reason;
        public final String administrator;
        public final LocalDateTime date;

        private History(ResultSet row) throws SQLException {
            name = row.getString("product_name");
            code = row.getString("product_code");
            reference = row.getString("product_reference");
            String value = row.getString("change_type");
            switch (value) {
                case "INITIAL": type = "초기 등록"; break;
                case "REGISTER":
                case "RESUME":
                case "UNLIST": type = Assortment.valueOf(value).label; break;
                default: type = Change.valueOf(value).label;
            }
            before = row.getInt("before_quantity");
            after = row.getInt("after_quantity");
            reason = row.getString("reason");
            administrator = row.getString("administrator_id");
            date = row.getObject("changed_at", LocalDateTime.class);
        }
    }
}
