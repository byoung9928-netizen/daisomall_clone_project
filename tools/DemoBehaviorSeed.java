import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

// Run only against a disposable local development schema. No payment API is used.
public final class DemoBehaviorSeed {
    private static final String RECEIPTS = "product_behavior_demo_seed";
    private static final String DEMO_MEMBER = "m.role='MEMBER' AND m.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'"
            + " AND m.login_id=CONCAT('demorank',RIGHT(m.member_id,4))"
            + " AND m.email=CONCAT(m.login_id,'@example.invalid')";
    private static final String DEMO_ORDER = "o.order_no REGEXP '^DEMO_RANK_V1_'"
            + " AND o.checkout_token=CONCAT('DEMO-CHECKOUT-',o.order_no)";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    enum Scenario { CART_PURCHASE, DIRECT_PURCHASE, VIEW_ONLY, WISH_ONLY, CART_LOST, PAYMENT_FAILED }

    private static final class Event {
        final Scenario scenario;
        final String member, product, type;
        final LocalDateTime utc;

        Event(Scenario scenario, String member, String product, String type, LocalDateTime seoulTime) {
            this.scenario = scenario;
            this.member = member;
            this.product = product;
            this.type = type;
            this.utc = seoulTime.minusHours(9);
        }
    }

    public static void main(String[] args) throws SQLException {
        if (args.length != 3 || !args[1].matches("daisomall_demo(?:_[a-z0-9_]{1,25})?")
                || !List.of("preview", "apply", "cleanup").contains(args[0])) {
            throw new IllegalArgumentException("Usage: DemoBehaviorSeed preview|apply|cleanup daisomall_demo[_suffix] mysql_user");
        }
        String password = System.getenv("DAISO_BEHAVIOR_SEED_PASSWORD");
        if (password == null) throw new IllegalArgumentException("Set DAISO_BEHAVIOR_SEED_PASSWORD for the local DB user.");
        Properties credentials = new Properties();
        credentials.setProperty("user", args[2]);
        credentials.setProperty("password", password);
        String schema = args[1];
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/" + schema
                + "?serverTimezone=Asia/Seoul&connectTimeout=5000&socketTimeout=10000", credentials)) {
            if (!schema.equals(conn.getCatalog())) throw new SQLException("Unexpected current database.");
            verifyEvents(conn);
            if ("preview".equals(args[0])) {
                report(plan(conn));
                return;
            }
            lock(conn);
            if ("cleanup".equals(args[0])) {
                if (!receiptsExist(conn)) {
                    System.out.println("No demo seed receipts. Deleted 0 events.");
                    return;
                }
                try (Statement statement = conn.createStatement()) {
                    System.out.println("Deleted generated events: " + statement.executeUpdate("DELETE e FROM product_behavior_events e"
                            + " JOIN " + RECEIPTS + " d ON d.event_id=e.event_id"));
                }
                return;
            }
            createReceipts(conn);
            List<Event> plan = plan(conn);
            report(plan);
            conn.setAutoCommit(false);
            try {
                int inserted = apply(conn, plan);
                conn.commit();
                System.out.println("Inserted events: " + inserted);
            } catch (SQLException | RuntimeException error) {
                try { conn.rollback(); }
                catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        }
    }

    private static List<Event> plan(Connection conn) throws SQLException {
        List<Event> plan = new ArrayList<>();
        Set<String> used = new HashSet<>();
        addPurchases(conn, plan, used, false);
        addPurchases(conn, plan, used, true);
        addNonBuyers(conn, plan);
        return plan;
    }

    private static void addPurchases(Connection conn, List<Event> plan, Set<String> used, boolean failed)
            throws SQLException {
        String match = failed
                ? " AND (o.status IN ('FAILED','PAYMENT_FAILED') OR EXISTS (SELECT 1 FROM payment_attempts p"
                    + " WHERE p.order_id=o.order_id AND p.method='KAKAOPAY' AND p.status IN ('FAILED','PAYMENT_FAILED')))"
                    + " AND NOT EXISTS (SELECT 1 FROM payment_attempts p WHERE p.order_id=o.order_id AND p.approved_at IS NOT NULL)"
                : " AND o.status='PAID' AND o.canceled_at IS NULL AND EXISTS (SELECT 1 FROM payment_attempts p"
                    + " WHERE p.order_id=o.order_id AND p.method='KAKAOPAY' AND p.status='APPROVED'"
                    + " AND p.amount=o.total_amount AND p.tid=CONCAT('DEMO-TID-',o.order_no)"
                    + " AND p.approved_at>=o.created_at AND p.approved_at<o.created_at+INTERVAL 6 DAY"
                    + " AND p.approved_at<=UTC_TIMESTAMP()+INTERVAL 9 HOUR)";
        String query = "WITH ranked AS (SELECT m.member_id,i.product_id,o.created_at,ROW_NUMBER() OVER"
                + " (PARTITION BY m.member_id,i.product_id ORDER BY o.created_at,o.order_id) n"
                + " FROM members m JOIN orders o USING(member_id) JOIN order_items i USING(order_id)"
                + " WHERE " + DEMO_MEMBER + " AND " + DEMO_ORDER
                + " AND o.created_at>UTC_TIMESTAMP()+INTERVAL 9 HOUR-INTERVAL 365 DAY"
                + " AND o.created_at<=UTC_TIMESTAMP()+INTERVAL 9 HOUR" + match + ")"
                + " SELECT member_id,product_id,created_at FROM ranked WHERE n=1 ORDER BY member_id,product_id LIMIT 60";
        try (PreparedStatement statement = conn.prepareStatement(query); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String member = rows.getString("member_id");
                String product = rows.getString("product_id");
                if (!used.add(member + "|" + product)) continue;
                LocalDateTime ordered = rows.getObject("created_at", LocalDateTime.class);
                boolean cart = failed || ((member + "|" + product).hashCode() & 1) == 0;
                Scenario scenario = failed ? Scenario.PAYMENT_FAILED : cart ? Scenario.CART_PURCHASE : Scenario.DIRECT_PURCHASE;
                plan.add(new Event(scenario, member, product, "VIEW", ordered.minusHours(2)));
                if (cart) plan.add(new Event(scenario, member, product, "CART_ADD", ordered.minusMinutes(30)));
            }
        }
    }

    private static void addNonBuyers(Connection conn, List<Event> plan) throws SQLException {
        String query = "WITH demo AS (SELECT m.member_id FROM members m WHERE " + DEMO_MEMBER
                + " ORDER BY m.member_id LIMIT 36), items AS (SELECT product_id FROM products WHERE status='AVAILABLE'"
                + " ORDER BY product_id LIMIT 50), ranked AS (SELECT d.member_id,p.product_id,"
                + "ROW_NUMBER() OVER (PARTITION BY d.member_id ORDER BY CRC32(CONCAT(d.member_id,'|',p.product_id)),p.product_id) n"
                + " FROM demo d CROSS JOIN items p WHERE NOT EXISTS (SELECT 1 FROM orders o"
                + " JOIN order_items i USING(order_id) WHERE o.member_id=d.member_id AND i.product_id=p.product_id))"
                + " SELECT member_id,product_id FROM ranked WHERE n=1 ORDER BY member_id";
        LocalDateTime first = LocalDate.now(SEOUL).minusDays(8).atTime(9, 0);
        Scenario[] roles = {Scenario.VIEW_ONLY, Scenario.WISH_ONLY, Scenario.CART_LOST};
        try (PreparedStatement statement = conn.prepareStatement(query); ResultSet rows = statement.executeQuery()) {
            for (int index = 0; rows.next(); index++) {
                Scenario role = roles[index % roles.length];
                String member = rows.getString(1);
                String product = rows.getString(2);
                plan.add(new Event(role, member, product, "VIEW", first));
                if (role != Scenario.VIEW_ONLY) {
                    plan.add(new Event(role, member, product, role == Scenario.WISH_ONLY ? "WISHLIST_ADD" : "CART_ADD",
                            first.plusMinutes(30)));
                }
            }
        }
    }

    private static void report(List<Event> plan) {
        Map<Scenario, Long> groups = new LinkedHashMap<>();
        for (Scenario role : Scenario.values()) groups.put(role, 0L);
        for (Event event : plan) if ("VIEW".equals(event.type)) groups.merge(event.scenario, 1L, Long::sum);
        groups.forEach((scenario, count) -> System.out.println(scenario + ": " + count));
        if (groups.get(Scenario.PAYMENT_FAILED) == 0) {
            System.out.println("No eligible existing failed demo order; no failed payment is invented.");
        }
    }

    private static void verifyEvents(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeQuery("SELECT event_id FROM product_behavior_events LIMIT 0").close();
        }
    }

    private static boolean receiptsExist(Connection conn) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("SELECT 1 FROM information_schema.TABLES"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            statement.setString(1, RECEIPTS);
            try (ResultSet row = statement.executeQuery()) { return row.next(); }
        }
    }

    private static void lock(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement(); ResultSet row = statement.executeQuery(
                "SELECT GET_LOCK(CONCAT(DATABASE(),':behavior-demo'),0)")) {
            if (!row.next() || row.getInt(1) != 1) throw new SQLException("Another demo seed operation is running.");
        }
    }

    private static void createReceipts(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + RECEIPTS + " (event_id BIGINT UNSIGNED PRIMARY KEY,"
                    + " member_id VARCHAR(100) NOT NULL,product_id VARCHAR(100) NOT NULL,scenario VARCHAR(30) NOT NULL,"
                    + " event_type VARCHAR(20) NOT NULL, UNIQUE KEY uq_demo_behavior (member_id,product_id,scenario,event_type),"
                    + " CONSTRAINT fk_demo_behavior_event FOREIGN KEY (event_id) REFERENCES product_behavior_events(event_id)"
                    + " ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        }
    }

    private static int apply(Connection conn, List<Event> plan) throws SQLException {
        int inserted = 0;
        try (PreparedStatement existing = conn.prepareStatement("SELECT 1 FROM " + RECEIPTS
                    + " WHERE member_id=? AND product_id=? AND scenario=? AND event_type=?");
                PreparedStatement nearby = conn.prepareStatement("SELECT 1 FROM product_behavior_events"
                    + " WHERE member_id=? AND product_id=? AND event_type=? AND occurred_at>=? AND occurred_at<=? LIMIT 1");
                PreparedStatement save = conn.prepareStatement("INSERT INTO product_behavior_events"
                    + " (member_id,product_id,event_type,occurred_at) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                PreparedStatement receipt = conn.prepareStatement("INSERT INTO " + RECEIPTS
                    + " (event_id,member_id,product_id,scenario,event_type) VALUES (?,?,?,?,?)")) {
            for (Event event : plan) {
                existing.setString(1, event.member);
                existing.setString(2, event.product);
                existing.setString(3, event.scenario.name());
                existing.setString(4, event.type);
                try (ResultSet found = existing.executeQuery()) { if (found.next()) continue; }
                long minutes = "VIEW".equals(event.type) ? 30 : 0;
                nearby.setString(1, event.member);
                nearby.setString(2, event.product);
                nearby.setString(3, event.type);
                nearby.setObject(4, event.utc.minusMinutes(minutes));
                nearby.setObject(5, event.utc.plusMinutes(minutes));
                try (ResultSet found = nearby.executeQuery()) { if (found.next()) continue; }

                save.setString(1, event.member);
                save.setString(2, event.product);
                save.setString(3, event.type);
                save.setObject(4, event.utc);
                save.executeUpdate();
                try (ResultSet keys = save.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Event ID was not returned.");
                    receipt.setLong(1, keys.getLong(1));
                }
                receipt.setString(2, event.member);
                receipt.setString(3, event.product);
                receipt.setString(4, event.scenario.name());
                receipt.setString(5, event.type);
                receipt.executeUpdate();
                inserted++;
            }
        }
        return inserted;
    }
}
