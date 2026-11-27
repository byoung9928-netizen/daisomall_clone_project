package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StoreInventoryDAO {
    public static final int DEFAULT_THRESHOLD = 5;
    private static final String STOCK_FIELDS =
            "SELECT p.product_id, COALESCE(i.quantity, 0) AS quantity, COALESCE(i.version, -1) AS version, "
            + "COALESCE(i.is_carried, 0) AS is_carried, p.name, p.image_url, COALESCE(p.product_code, '') AS product_code, ";
    private static final String SEARCH = " AND (LOCATE(?, %s) > 0 OR LOCATE(?, %s) > 0)";

    private static final String STOCK_SEARCH = String.format(SEARCH, "p.name", "p.product_code");
    private static final String MANAGER_SEARCH =
            " AND (LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0"
            + " OR LOCATE(?, p.brand) > 0 OR LOCATE(?, p.product_id) > 0)";
    private static final String HISTORY_SCOPE = " FROM store_inventory_history WHERE store_id = ?"
            + String.format(SEARCH, "product_name", "product_code");

    private static final String PICKUP_FROM = " FROM store_inventory i JOIN store_locations s ON s.store_id = i.store_id";
    private static final String PICKUP_IN_STOCK = " AND i.is_carried = 1 AND i.quantity > 0";

    private final boolean thresholdsEnabled;
    private final boolean transferEnabled;

    public StoreInventoryDAO() {
        this(false, false);
    }

    private StoreInventoryDAO(boolean thresholdsEnabled, boolean transferEnabled) {
        this.thresholdsEnabled = thresholdsEnabled;
        this.transferEnabled = transferEnabled;
    }

    public static StoreInventoryDAO forInventory(Connection connection) throws SQLException {
        String sql = "SELECT SUM(COLUMN_NAME <> 'transfer_id'), SUM(COLUMN_NAME = 'transfer_id') "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND "
                + "((TABLE_NAME = 'store_inventory' AND COLUMN_NAME = 'low_stock_threshold') "
                + "OR (TABLE_NAME = 'store_inventory_history' AND COLUMN_NAME IN ('before_threshold','after_threshold','transfer_id')))";
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            row.next();
            return new StoreInventoryDAO(row.getInt(1) == 3, row.getInt(2) == 1);
        }
    }

    public boolean thresholdsEnabled() {
        return thresholdsEnabled;
    }

    private String thresholdExpression() {
        return thresholdsEnabled ? "i.low_stock_threshold" : Integer.toString(DEFAULT_THRESHOLD);
    }

    private String stockFields() {
        return STOCK_FIELDS + "COALESCE(" + thresholdExpression() + ", " + DEFAULT_THRESHOLD + ") AS low_stock_threshold ";
    }

    public enum State {
        ALL("전체"), NORMAL("정상"), LOW("재고 부족"), SOLD_OUT("품절 (0개)");

        public final String label;
        State(String label) { this.label = label; }

        public static State ofQuantity(int quantity) {
            return ofQuantity(quantity, DEFAULT_THRESHOLD);
        }

        public static State ofQuantity(int quantity, int threshold) {
            return quantity == 0 ? SOLD_OUT : quantity <= threshold ? LOW : NORMAL;
        }

        private String predicate(String threshold) {
            switch (this) {
                case NORMAL: return " AND i.quantity > " + threshold;
                case LOW: return " AND i.quantity BETWEEN 1 AND " + threshold;
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

    public List<PickupStore> pickupStores(Connection connection, String productId) throws SQLException {
        String sql = "SELECT s.store_id, s.store_name, s.city, i.quantity"
                + PICKUP_FROM + " WHERE i.product_id = ?" + PICKUP_IN_STOCK
                + " ORDER BY s.city, s.store_name, s.store_id";
        List<PickupStore> stores = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, productId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    stores.add(new PickupStore(rows.getString("store_id"), rows.getString("store_name"),
                            rows.getString("city"), rows.getInt("quantity")));
                }
            }
        }
        return stores;
    }

    public Set<String> pickupProductIds(Connection connection, Collection<String> productIds) throws SQLException {
        List<String> ids = List.copyOf(new HashSet<>(productIds));
        if (ids.isEmpty()) return Set.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT DISTINCT i.product_id" + PICKUP_FROM
                + " WHERE i.product_id IN (" + placeholders + ")" + PICKUP_IN_STOCK;
        Set<String> result = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) query.setString(i + 1, ids.get(i));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(rows.getString("product_id"));
            }
        }
        return result;
    }

    public static final class InventoryFilter {
        private final String store;
        private final String keyword;
        private final String category;
        private final String productId;
        private final State state;
        private final Integer minimum;
        private final Integer maximum;
        private final boolean managerSearch;

        public InventoryFilter(String store, String keyword, String category, String productId, State state) {
            this(store, keyword, category, productId, state, null, null, false);
        }

        public InventoryFilter(String store, String keyword, String category, String productId, State state,
                Integer minimum, Integer maximum) {
            this(store, keyword, category, productId, state, minimum, maximum, true);
        }

        private InventoryFilter(String store, String keyword, String category, String productId, State state,
                Integer minimum, Integer maximum, boolean managerSearch) {
            this.store = store;
            this.keyword = keyword;
            this.category = category;
            this.productId = productId;
            this.state = state;
            this.minimum = minimum;
            this.maximum = maximum;
            this.managerSearch = managerSearch;
        }

        private int bind(PreparedStatement statement) throws SQLException {
            return bind(statement, 1);
        }

        private int bind(PreparedStatement statement, int next) throws SQLException {
            statement.setString(next++, store);
            for (int i = 0; i < (managerSearch ? 4 : 2); i++) statement.setString(next++, keyword);
            if (!category.isEmpty()) statement.setString(next++, category);
            if (!productId.isEmpty()) statement.setString(next++, productId);
            if (minimum != null) statement.setInt(next++, minimum);
            if (maximum != null) statement.setInt(next++, maximum);
            return next;
        }
    }

    public List<Stock> inventory(Connection connection, InventoryFilter filter, Sort sort, int limit, int offset)
            throws SQLException {
        String sql = stockFields() + inventoryScope(filter) + " ORDER BY " + sort.order + ", p.product_id LIMIT ? OFFSET ?";
        List<Stock> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = filter.bind(statement);
            statement.setInt(next, limit);
            statement.setInt(next + 1, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readStock(rows));
            }
        }
        return result;
    }

    public enum ManagerSort {
        NAME("상품명", "p.name", false), NAME_DESC("상품명", "p.name", true),
        CODE("품번", "p.product_code", false), CODE_DESC("품번", "p.product_code", true),
        QUANTITY("재고", "i.quantity", false), QUANTITY_DESC("재고", "i.quantity", true),
        THRESHOLD("부족 기준", "low_stock_threshold", false), THRESHOLD_DESC("부족 기준", "low_stock_threshold", true);

        private final String order;
        public final String label;
        public final boolean descending;
        ManagerSort(String label, String column, boolean descending) {
            this.label = label;
            this.descending = descending;
            this.order = column + (descending ? " DESC" : " ASC");
        }

        public ManagerSort reverse() { return values()[ordinal() ^ 1]; }
    }

    public static final class InventorySnapshot {
        public final Stock stock;
        public final String categoryId;
        public final LocalDateTime lastQuantityChange;

        private InventorySnapshot(ResultSet row) throws SQLException {
            stock = readStock(row);
            categoryId = row.getString("category_id");
            lastQuantityChange = row.getObject("last_quantity_change", LocalDateTime.class);
        }
    }

    public List<InventorySnapshot> inventorySnapshots(Connection connection, InventoryFilter filter, ManagerSort sort,
            int limit, long offset) throws SQLException {
        String history = " LEFT JOIN (SELECT product_id, MAX(changed_at) AS last_quantity_change"
                + " FROM store_inventory_history WHERE store_id = ? AND product_id IS NOT NULL"
                + " AND before_quantity <> after_quantity GROUP BY product_id) h ON h.product_id = i.product_id";
        String sql = stockFields() + ", p.category_id, h.last_quantity_change"
                + inventoryScope(filter, history) + " ORDER BY " + sort.order + ", p.product_id LIMIT ? OFFSET ?";
        List<InventorySnapshot> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, filter.store);
            int next = filter.bind(statement, 2);
            statement.setInt(next, limit);
            statement.setLong(next + 1, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new InventorySnapshot(rows));
            }
        }
        return result;
    }

    public long countInventory(Connection connection, InventoryFilter filter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*)" + inventoryScope(filter))) {
            filter.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    public static final class StockSuggestion {
        public final Stock stock;
        public final int price;

        private StockSuggestion(ResultSet row) throws SQLException {
            stock = readStock(row);
            price = row.getInt("price");
        }
    }

    public List<StockSuggestion> suggestInventory(Connection connection, InventoryFilter filter) throws SQLException {
        if (filter.keyword.isEmpty()) return List.of();
        String sql = stockFields() + ", p.price" + inventoryScope(filter)
                + " ORDER BY CASE WHEN p.product_code = ? OR p.name = ? THEN 0 "
                + "WHEN LOCATE(?, p.name) = 1 OR LOCATE(?, p.product_code) = 1 THEN 1 ELSE 2 END, "
                + "CHAR_LENGTH(p.name), p.name, p.product_id LIMIT 10";
        List<StockSuggestion> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = filter.bind(statement);
            for (int i = 0; i < 4; i++) statement.setString(next + i, filter.keyword);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new StockSuggestion(rows));
            }
        }
        return result;
    }

    private String inventoryScope(InventoryFilter filter) {
        return inventoryScope(filter, "");
    }

    private String inventoryScope(InventoryFilter filter, String join) {
        String scope = " FROM store_inventory i JOIN products p ON p.product_id = i.product_id" + join
                + " WHERE i.store_id = ? AND i.is_carried = 1" + filter.state.predicate(thresholdExpression())
                + (filter.managerSearch ? MANAGER_SEARCH : STOCK_SEARCH);
        if (!filter.category.isEmpty()) {
            scope += " AND p.category_id IN (WITH RECURSIVE descendants AS ("
                    + "SELECT category_id FROM categories WHERE category_id = ? UNION ALL "
                    + "SELECT c.category_id FROM categories c JOIN descendants d ON c.parent_id = d.category_id) "
                    + "SELECT category_id FROM descendants)";
        }
        if (!filter.productId.isEmpty()) scope += " AND p.product_id = ?";
        if (filter.minimum != null) scope += " AND i.quantity >= ?";
        if (filter.maximum != null) scope += " AND i.quantity <= ?";
        return scope;
    }

    public enum AssortmentFilter {
        UNREGISTERED("미등록", " AND i.product_id IS NULL"),
        ACTIVE("취급 중", " AND i.is_carried = 1"),
        ENDED("취급 종료", " AND i.is_carried = 0"),
        ALL("전체", "");

        public final String label;
        private final String predicate;
        AssortmentFilter(String label, String predicate) {
            this.label = label;
            this.predicate = predicate;
        }
    }

    public List<Stock> assortment(Connection connection, String store, String keyword, AssortmentFilter filter,
            int limit, int offset)
            throws SQLException {
        return stockPage(connection, assortmentScope(filter), store, keyword, Sort.NAME, limit, offset);
    }

    public long countAssortment(Connection connection, String store, String keyword, AssortmentFilter filter)
            throws SQLException {
        return count(connection, assortmentScope(filter), store, keyword);
    }

    private static String assortmentScope(AssortmentFilter filter) {
        return " FROM products p LEFT JOIN store_inventory i "
                + "ON i.product_id = p.product_id AND i.store_id = ? WHERE 1 = 1" + filter.predicate + STOCK_SEARCH;
    }

    private List<Stock> stockPage(Connection connection, String scope, String store, String keyword,
            Sort sort, int limit, int offset) throws SQLException {
        String sql = stockFields() + scope + " ORDER BY " + sort.order + ", p.product_id LIMIT ? OFFSET ?";
        List<Stock> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPage(statement, store, keyword, limit, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readStock(rows));
            }
        }
        return result;
    }

    public enum Sort {
        NAME("상품명순", "p.name"), HIGH("재고 많은순", "i.quantity DESC"),
        LOW("재고 적은순", "i.quantity"), SHORTAGE("부족 기준 대비 적은순", "CAST(i.quantity AS SIGNED) - low_stock_threshold"),
        REGION("지역순", "p.name");

        public final String label;
        private final String order;

        Sort(String label, String order) { this.label = label; this.order = order; }
    }

    public List<Stock> compareCandidates(Connection connection, String keyword) throws SQLException {
        String sql = "SELECT p.product_id, p.name, p.image_url, COALESCE(p.product_code,'') AS product_code, "
                + "0 AS quantity, -1 AS version, 0 AS is_carried, " + DEFAULT_THRESHOLD + " AS low_stock_threshold "
                + "FROM products p WHERE LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0 "
                + "ORDER BY p.name, p.product_id LIMIT 25";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, keyword);
            query.setString(2, keyword);
            try (ResultSet rows = query.executeQuery()) {
                List<Stock> results = new ArrayList<>();
                while (rows.next()) results.add(readStock(rows));
                return results;
            }
        }
    }

    public List<ComparedStock> compare(Connection connection, String product, String city, Sort sort) throws SQLException {
        String order;
        switch (sort) {
            case HIGH: order = "i.quantity DESC"; break;
            case LOW: order = "i.quantity"; break;
            case SHORTAGE: order = "CAST(i.quantity AS SIGNED) - threshold"; break;
            default: order = "s.city, s.store_name";
        }
        String threshold = thresholdsEnabled ? "i.low_stock_threshold" : Integer.toString(DEFAULT_THRESHOLD);
        String sql = "SELECT s.store_id, s.store_name, s.city, i.quantity, " + threshold + " AS threshold "
                + "FROM store_locations s JOIN products p ON p.product_id = ? LEFT JOIN store_inventory i "
                + "ON i.store_id = s.store_id AND i.product_id = p.product_id AND i.is_carried = 1 "
                + "WHERE (? = '' OR s.city = ?) ORDER BY i.quantity IS NULL, " + order + ", s.store_id";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, product);
            query.setString(2, city);
            query.setString(3, city);
            try (ResultSet rows = query.executeQuery()) {
                List<ComparedStock> result = new ArrayList<>();
                while (rows.next()) result.add(new ComparedStock(rows));
                return result;
            }
        }
    }

    public static final class ComparedStock {
        public final String store;
        public final String name;
        public final String city;
        public final Integer quantity;
        public final int threshold;

        ComparedStock(ResultSet rows) throws SQLException {
            store = rows.getString(1);
            name = rows.getString(2);
            city = rows.getString(3);
            quantity = rows.getObject("quantity", Integer.class);
            threshold = rows.getInt("threshold");
        }

        public State state() { return quantity == null ? null : State.ofQuantity(quantity, threshold); }
    }

    public Summary summary(Connection connection, String store) throws SQLException {
        String sql = "SELECT COUNT(*) AS kinds, COALESCE(SUM(quantity), 0) AS units, "
                + "COUNT(CASE WHEN quantity BETWEEN 1 AND " + thresholdExpression() + " THEN 1 END) AS low, "
                + "COUNT(CASE WHEN quantity = 0 THEN 1 END) AS sold_out "
                + "FROM store_inventory i WHERE store_id = ? AND is_carried = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, store);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return new Summary(row.getLong("kinds"), row.getLong("units"),
                        row.getLong("low"), row.getLong("sold_out"));
            }
        }
    }

    public static final class HistoryFilter {
        public final LocalDateTime start;
        public final LocalDateTime end;
        public final String type;
        public final String actor;
        public final String reference;
        public final boolean oldest;
        private final String productReference;

        public HistoryFilter(LocalDateTime start, LocalDateTime end, String type, String actor, String reference, boolean oldest) {
            this(start, end, type, actor, reference, oldest, "");
        }

        public HistoryFilter(LocalDateTime start, LocalDateTime end, String type, String actor, String reference,
                boolean oldest, String productReference) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.actor = actor;
            this.reference = reference;
            this.oldest = oldest;
            this.productReference = productReference;
        }

        String scope(boolean transfers) {
            return HISTORY_SCOPE + (start == null ? "" : " AND changed_at >= ? AND changed_at < ?")
                    + (type.isEmpty() ? "" : "TRANSFER".equals(type)
                            ? (transfers ? " AND transfer_id IS NOT NULL" : " AND FALSE") : " AND change_type = ?")
                    + (actor.isEmpty() ? "" : " AND administrator_id = ?")
                    + (reference.isEmpty() ? "" : transfers ? " AND transfer_id = ?" : " AND FALSE")
                    + (productReference.isEmpty() ? "" : " AND product_reference = ?");
        }

        int bind(PreparedStatement query, String store, String keyword, boolean transfers) throws SQLException {
            bindScope(query, store, keyword);
            int i = 4;
            if (start != null) { query.setObject(i++, start); query.setObject(i++, end); }
            if (!type.isEmpty() && !"TRANSFER".equals(type)) query.setString(i++, type);
            if (!actor.isEmpty()) query.setString(i++, actor);
            if (!reference.isEmpty() && transfers) query.setString(i++, reference);
            if (!productReference.isEmpty()) query.setString(i++, productReference);
            return i;
        }
    }

    public List<History> history(Connection connection, String store, String keyword, HistoryFilter filter, int limit, int offset)
            throws SQLException {
        String thresholdFields = thresholdsEnabled ? "before_threshold, after_threshold"
                : "NULL AS before_threshold, NULL AS after_threshold";
        String sql = "SELECT product_name, product_code, product_reference, change_type, before_quantity, "
                + "after_quantity, reason, administrator_id, changed_at, " + thresholdFields
                + (transferEnabled ? ", transfer_id" : ", NULL AS transfer_id") + filter.scope(transferEnabled)
                + (filter.oldest ? " ORDER BY changed_at ASC, history_id ASC" : " ORDER BY changed_at DESC, history_id DESC")
                + " LIMIT ? OFFSET ?";
        List<History> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            int index = filter.bind(query, store, keyword, transferEnabled);
            query.setInt(index, limit);
            query.setInt(index + 1, offset);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new History(rows));
            }
        }
        return result;
    }

    public long countHistory(Connection connection, String store, String keyword, HistoryFilter filter) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*)" + filter.scope(transferEnabled))) {
            filter.bind(query, store, keyword, transferEnabled);
            try (ResultSet row = query.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }

    private static long count(Connection connection, String scope, String store, String keyword) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*)" + scope)) {
            bindScope(statement, store, keyword);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    public StockAvailability availability(Connection connection, String store, String product) throws SQLException {
        if (!thresholdsEnabled) {
            Integer quantity = quantity(connection, store, product);
            return quantity == null ? null : new StockAvailability(quantity, DEFAULT_THRESHOLD);
        }
        String sql = "SELECT quantity, low_stock_threshold FROM store_inventory "
                + "WHERE store_id = ? AND product_id = ? AND is_carried = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, store);
            statement.setString(2, product);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? new StockAvailability(row.getInt("quantity"), row.getInt("low_stock_threshold")) : null;
            }
        }
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

    /**
     * Resolve the stored keys using the same collation as checkout's order_items.product_id.
     * This read takes no row locks. Callers lock individual inventory PKs in the returned order.
     */
    public List<String> inventoryLockOrder(Connection connection, String store, Collection<String> products)
            throws SQLException {
        if (products.size() <= 1) return List.copyOf(products);
        String sql = "SELECT product_id FROM store_inventory WHERE store_id = ? AND product_id IN ("
                + String.join(",", Collections.nCopies(products.size(), "?")) + ") ORDER BY product_id";
        return orderedKeys(connection, sql, store, products);
    }

    public List<String> storeLockOrder(Connection connection, Collection<String> stores) throws SQLException {
        if (stores.size() <= 1) return List.copyOf(stores);
        String sql = "SELECT store_id FROM store_locations WHERE store_id IN ("
                + String.join(",", Collections.nCopies(stores.size(), "?")) + ") ORDER BY store_id";
        return orderedKeys(connection, sql, null, stores);
    }

    private static List<String> orderedKeys(Connection connection, String sql, String store, Collection<String> keys)
            throws SQLException {
        Set<String> expected = new HashSet<>(keys);
        List<String> ordered = new ArrayList<>(expected.size());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int bind = 1;
            if (store != null) statement.setString(bind++, store);
            for (String key : keys) statement.setString(bind++, key);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String key = rows.getString(1);
                    if (expected.remove(key)) ordered.add(key);
                }
            }
        }
        if (!expected.isEmpty()) throw new IllegalArgumentException("매장 또는 상품이 변경되었습니다. 최신 목록에서 다시 선택해 주세요.");
        return ordered;
    }

    public Stock find(Connection connection, String store, String product, boolean lock) throws SQLException {
        String sql = stockFields() + "FROM store_inventory i JOIN products p ON p.product_id = i.product_id "
                + "WHERE i.store_id = ? AND i.product_id = ?" + (lock ? " FOR UPDATE OF i" : "");
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
        update(connection, store, before, after, change, reason, administrator, null);
    }

    public void update(Connection connection, String store, Stock before, int after, Change change, String reason,
            String administrator, String transferId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE store_inventory "
                + "SET quantity = ?, version = version + 1 "
                + "WHERE store_id = ? AND product_id = ? AND version = ? AND is_carried = 1")) {
            statement.setInt(1, after);
            statement.setString(2, store);
            statement.setString(3, before.id);
            statement.setLong(4, before.version);
            if (statement.executeUpdate() != 1) throw new SQLException("Store inventory row could not be updated");
        }
        recordChange(connection, store, before, after, change.name(), reason, administrator, null, transferId);
    }

    public void updateThreshold(Connection connection, String store, Stock before, int threshold,
            String administrator) throws SQLException {
        updateThreshold(connection, store, before, threshold, administrator, "부족 기준 변경");
    }

    public void updateThreshold(Connection connection, String store, Stock before, int threshold,
            String administrator, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE store_inventory "
                + "SET low_stock_threshold = ?, version = version + 1 "
                + "WHERE store_id = ? AND product_id = ? AND version = ? AND is_carried = 1")) {
            statement.setInt(1, threshold);
            statement.setString(2, store);
            statement.setString(3, before.id);
            statement.setLong(4, before.version);
            if (statement.executeUpdate() != 1) throw new SQLException("Store threshold row could not be updated");
        }
        recordChange(connection, store, before, before.quantity, "THRESHOLD", reason, administrator, threshold, null);
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
                + "COALESCE(p.product_code, '') AS product_code, 0 AS quantity, -1 AS version, 0 AS is_carried, "
                + DEFAULT_THRESHOLD + " AS low_stock_threshold "
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
        recordChange(connection, store, before, after, type, reason, administrator, null, null);
    }

    private void recordChange(Connection connection, String store, Stock before, int after, String type,
            String reason, String administrator, Integer threshold, String transferId) throws SQLException {
        String thresholdColumns = threshold == null ? "" : ", before_threshold, after_threshold";
        String thresholdValues = threshold == null ? "" : ", ?, ?";
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO store_inventory_history "
                + "(store_id, product_id, product_reference, product_name, product_code, change_type, "
                + "before_quantity, after_quantity, reason, administrator_id" + thresholdColumns
                + (transferId == null ? "" : ", transfer_id")
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?" + thresholdValues
                + (transferId == null ? "" : ", ?") + ")")) {
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
            if (threshold != null) {
                statement.setInt(11, before.threshold);
                statement.setInt(12, threshold);
            }
            if (transferId != null) statement.setString(11, transferId);
            statement.executeUpdate();
        }
    }

    private static void bindPage(PreparedStatement statement, String store, String keyword, int limit, int offset)
            throws SQLException {
        bindScope(statement, store, keyword);
        statement.setInt(4, limit);
        statement.setInt(5, offset);
    }

    private static void bindScope(PreparedStatement statement, String store, String keyword) throws SQLException {
        statement.setString(1, store);
        statement.setString(2, keyword);
        statement.setString(3, keyword);
    }

    private static Stock readStock(ResultSet row) throws SQLException {
        return new Stock(row.getString("product_id"), row.getString("name"), row.getString("product_code"),
                row.getString("image_url"), row.getInt("quantity"), row.getLong("version"),
                row.getBoolean("is_carried"), row.getInt("low_stock_threshold"));
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

    public static final class PickupStore {
        public final Location location;
        public final int quantity;

        private PickupStore(String id, String name, String city, int quantity) {
            this.location = new Location(id, name, city);
            this.quantity = quantity;
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

    public static final class StockAvailability {
        public final int quantity;
        public final State state;

        private StockAvailability(int quantity, int threshold) {
            this.quantity = quantity;
            this.state = State.ofQuantity(quantity, threshold);
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
        public final int threshold;

        private Stock(String id, String name, String code, String image, int quantity, long version, boolean carried,
                int threshold) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.image = image;
            this.quantity = quantity;
            this.version = version;
            this.carried = carried;
            this.threshold = threshold;
        }

        public State state() {
            return State.ofQuantity(quantity, threshold);
        }
    }

    public static final class History {
        public final String name;
        public final String code;
        public final String reference;
        public final String type;
        public final int before;
        public final int after;
        public final Integer beforeThreshold;
        public final Integer afterThreshold;
        public final String reason;
        public final String administrator;
        public final LocalDateTime date;
        public final String transferId;

        private History(ResultSet row) throws SQLException {
            name = row.getString("product_name");
            code = row.getString("product_code");
            reference = row.getString("product_reference");
            String value = row.getString("change_type");
            switch (value) {
                case "INITIAL": type = "초기 등록"; break;
                case "THRESHOLD": type = "부족 기준 변경"; break;
                case "REGISTER":
                case "RESUME":
                case "UNLIST": type = Assortment.valueOf(value).label; break;
                default: type = Change.valueOf(value).label;
            }
            before = row.getInt("before_quantity");
            after = row.getInt("after_quantity");
            beforeThreshold = row.getObject("before_threshold", Integer.class);
            afterThreshold = row.getObject("after_threshold", Integer.class);
            reason = row.getString("reason");
            administrator = row.getString("administrator_id");
            date = row.getObject("changed_at", LocalDateTime.class);
            transferId = row.getString("transfer_id");
        }
    }
}
