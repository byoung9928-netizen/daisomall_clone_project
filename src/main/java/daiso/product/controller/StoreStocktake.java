package daiso.product.controller;

import com.google.gson.JsonObject;
import daiso.order.db.PickupStockTransaction;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Stock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/** Shared stocktake calculation and session baseline for both inventory workspaces. */
final class StoreStocktake {
    private static final String SESSION_KEY = StoreStocktake.class.getName();
    private static final int BASELINE_LIMIT = 12;
    private static final long BASELINE_TTL_MILLIS = 600_000;
    private static final String STALE = "실사 기준이 바뀌었거나 만료되었습니다. 실제 수량을 다시 세어 주세요.";

    private StoreStocktake() { }

    static boolean enabled(Connection connection) throws SQLException {
        return Boolean.getBoolean("daisomall.storeStocktake") && PickupStockTransaction.enabled(connection);
    }

    static void beginCurrentRead(Connection connection) throws SQLException {
        // Set only this transaction. A pooled session's default isolation is left alone.
        // Discard any read transaction opened by connection-pool validation.
        connection.rollback();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED");
            statement.execute("START TRANSACTION");
        }
    }

    static String revision(Connection connection, String store) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT CAST(pickup_revision AS CHAR) FROM store_locations WHERE store_id = ?")) {
            query.setString(1, store);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new Conflict();
                return rows.getString(1);
            }
        }
    }

    static void requireAdministrator(Connection connection, String member, boolean lock) throws SQLException {
        String sql = "SELECT member_id FROM members WHERE member_id = ? AND role = 'ADMIN' AND status = 'ACTIVE'"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, member);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new Conflict("관리자 권한을 다시 확인해 주세요.");
            }
        }
    }

    static Baseline issue(Connection connection, HttpSession session, String member, String role, String store,
            Stock stock) throws SQLException {
        Baseline baseline = new Baseline(member, role, store, stock, revision(connection, store),
                StoreInventoryCountPreview.snapshot(connection, store, stock.id));
        synchronized (session) {
            Map<String, Baseline> baselines = baselines(session);
            if (baselines.size() >= BASELINE_LIMIT) baselines.remove(baselines.keySet().iterator().next());
            baselines.put(baseline.token, baseline);
        }
        return baseline;
    }

    static void present(JsonObject result, String store, Stock stock, long allocated, Baseline baseline) {
        result.addProperty("store", store);
        result.addProperty("item", stock.id);
        result.addProperty("available", stock.quantity);
        result.addProperty("allocated", allocated);
        result.addProperty("estimatedPhysical", stock.quantity + allocated);
        result.addProperty("version", Long.toString(stock.version));
        result.addProperty("checkedAt", (baseline == null
                ? OffsetDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0) : baseline.started).toString());
        if (baseline != null) result.addProperty("baseline", baseline.token);
    }

    static String respond(HttpServletResponse response, JsonObject result, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(result.toString());
        response.flushBuffer();
        return null;
    }

    static Baseline find(HttpSession session, String token, String member, String role, String store, String product,
            boolean consume) {
        synchronized (session) {
            Map<String, Baseline> baselines = baselines(session);
            Baseline value = baselines.get(token);
            if (value == null || value.expiresAt < System.currentTimeMillis() || !value.member.equals(member)
                    || !value.role.equals(role) || !value.store.equals(store) || !value.stock.id.equals(product)) {
                throw new Conflict();
            }
            if (consume) baselines.remove(token);
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Baseline> baselines(HttpSession session) {
        Map<String, Baseline> values = (Map<String, Baseline>) session.getAttribute(SESSION_KEY);
        if (values == null) {
            values = new LinkedHashMap<>();
            session.setAttribute(SESSION_KEY, values);
        }
        return values;
    }

    static int compare(Connection connection, Baseline baseline, Stock current, String revision, int physical)
            throws SQLException {
        if (current == null || !current.carried || !current.id.equals(baseline.stock.id)
                || current.version != baseline.stock.version || current.quantity != baseline.stock.quantity
                || !baseline.revision.equals(revision)) {
            throw new Conflict();
        }
        StoreInventoryCountPreview.Snapshot actual =
                StoreInventoryCountPreview.snapshot(connection, baseline.store, current.id);
        if (actual.allocated != baseline.orders.allocated || !actual.fingerprint.equals(baseline.orders.fingerprint)) {
            throw new Conflict();
        }
        if (physical < actual.allocated) throw new IllegalArgumentException("실제 보유 수량이 미수령 픽업 할당보다 적습니다.");
        return physical - actual.allocated;
    }

    static void save(Connection connection, StoreInventoryDAO dao, Baseline baseline, Stock current,
            int physical, int available, String reason) throws SQLException {
        if (available != current.quantity) {
            dao.updateCount(connection, baseline.store, current, available, physical, baseline.orders.allocated,
                    reason, baseline.member);
        }
    }

    static final class Baseline {
        final String token = UUID.randomUUID().toString();
        final OffsetDateTime started = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
        final long expiresAt = System.currentTimeMillis() + BASELINE_TTL_MILLIS;
        final String member;
        final String role;
        final String store;
        final Stock stock;
        final String revision;
        final StoreInventoryCountPreview.Snapshot orders;

        Baseline(String member, String role, String store, Stock stock, String revision,
                StoreInventoryCountPreview.Snapshot orders) {
            this.member = member;
            this.role = role;
            this.store = store;
            this.stock = stock;
            this.revision = revision;
            this.orders = orders;
        }
    }

    static final class Conflict extends IllegalArgumentException {
        Conflict() { this(STALE); }
        Conflict(String message) { super(message); }
    }
}

