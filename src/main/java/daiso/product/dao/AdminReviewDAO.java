package daiso.product.dao;

import daiso.product.dto.ReviewExtras.Answer;
import daiso.product.dto.ReviewFeedback.ReportStatus;
import daiso.product.dao.ReviewFeedbackDAO.ReportCounts;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdminReviewDAO {
    public enum State { ALL, ACTIVE, HIDDEN, REMOVED, DELETED }
    public enum ReportFilter { ALL, REPORTED, PENDING }

    public enum Moderation {
        PUBLISHED, HIDDEN, DELETED;

        boolean mayChangeTo(Moderation next) {
            switch (next) {
                case PUBLISHED: return this != PUBLISHED;
                case HIDDEN: return this == PUBLISHED;
                case DELETED: return this != DELETED;
                default: throw new IllegalArgumentException();
            }
        }
    }
    private final ReviewExtrasDAO extras = new ReviewExtrasDAO();

    private final ReviewFeedbackDAO feedback = new ReviewFeedbackDAO();

    public Page list(Connection connection, String keyword, int rating, State state, ReportFilter reportFilter,
            int requestedPage)
            throws SQLException {
        StringBuilder scope = new StringBuilder(" FROM product_reviews r "
                + "JOIN products p ON p.product_id = r.product_id "
                + "JOIN members m ON m.member_id = r.member_id "
                + "LEFT JOIN product_review_moderation rm ON rm.review_id = r.review_id WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (!keyword.isEmpty()) {
            scope.append(" AND (LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0 "
                    + "OR LOCATE(?, m.login_id) > 0 OR LOCATE(?, r.content) > 0)");
            for (int field = 0; field < 4; field++) parameters.add(keyword);
        }
        if (rating > 0) {
            scope.append(" AND r.rating = ?");
            parameters.add(rating);
        }
        if (state == State.DELETED) scope.append(" AND r.deleted_at IS NOT NULL");
        if (state == State.ACTIVE || state == State.HIDDEN || state == State.REMOVED) {
            scope.append(" AND r.deleted_at IS NULL AND COALESCE(rm.moderation_state, 'PUBLISHED') = ?");
            parameters.add(state == State.ACTIVE ? Moderation.PUBLISHED.name()
                    : state == State.HIDDEN ? Moderation.HIDDEN.name() : Moderation.DELETED.name());
        }

        boolean reportsAvailable = feedback.reportsAvailable(connection);
        if (reportFilter != ReportFilter.ALL) {
            if (!reportsAvailable) scope.append(" AND 1 = 0");
            else {
                scope.append(" AND EXISTS (SELECT 1 FROM product_review_reports rr WHERE rr.review_id = r.review_id");
                if (reportFilter == ReportFilter.PENDING) scope.append(" AND rr.status = 'PENDING'");
                scope.append(")");
            }
        }

        long total;
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*)" + scope)) {
            bind(query, parameters);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                total = rows.getLong(1);
            }
        }
        int limit = 20;
        long pages = Math.max(1, (total + limit - 1) / limit);
        int page = (int) Math.min(requestedPage, pages);
        List<Row> items = new ArrayList<>();
        String columns = "SELECT r.review_id, r.product_id, p.product_code, p.name, m.login_id, r.rating, "
                + "r.content, r.created_at, r.deleted_at, r.version, rm.changed_at, "
                + "COALESCE(rm.moderation_state, 'PUBLISHED') AS moderation_state";
        try (PreparedStatement query = connection.prepareStatement(columns + scope
                + " ORDER BY r.created_at DESC, r.review_id DESC LIMIT ? OFFSET ?")) {
            bind(query, parameters);
            query.setInt(parameters.size() + 1, limit);
            query.setLong(parameters.size() + 2, (page - 1L) * limit);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) items.add(new Row(rows));
            }
        }
        List<Long> ids = items.stream().map(item -> item.id).collect(Collectors.toList());
        boolean attachmentsAvailable = !ids.isEmpty() && extras.available(connection);
        return new Page(items, total, page, pages,
                attachmentsAvailable ? extras.answers(connection, ids) : Map.of(),
                attachmentsAvailable ? extras.photos(connection, ids) : Map.of(), reportsAvailable,
                reportsAvailable ? feedback.reportCounts(connection, ids) : Map.of());
    }

    public boolean moderate(Connection connection, long reviewId, long version, String administrator,
            Moderation next) throws SQLException {
        String lock = "SELECT COALESCE(rm.moderation_state, 'PUBLISHED') "
                + "FROM product_reviews r LEFT JOIN product_review_moderation rm ON rm.review_id = r.review_id "
                + "WHERE r.review_id = ? AND r.version = ? AND r.deleted_at IS NULL FOR UPDATE";
        try (PreparedStatement query = connection.prepareStatement(lock)) {
            query.setLong(1, reviewId);
            query.setLong(2, version);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !Moderation.valueOf(rows.getString(1)).mayChangeTo(next)) return false;
            }
        }

        String save = "INSERT INTO product_review_moderation (review_id, moderation_state, admin_member_id) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE moderation_state = ?, admin_member_id = ?, "
                + "changed_at = CURRENT_TIMESTAMP(6)";
        try (PreparedStatement query = connection.prepareStatement(save)) {
            query.setLong(1, reviewId);
            query.setString(2, next.name());
            query.setString(3, administrator);
            query.setString(4, next.name());
            query.setString(5, administrator);
            query.executeUpdate();
        }
        try (PreparedStatement query = connection.prepareStatement(
                "UPDATE product_reviews SET version = version + 1 WHERE review_id = ?")) {
            query.setLong(1, reviewId);
            if (query.executeUpdate() != 1) throw new SQLException("리뷰 버전 갱신 실패");
        }
        if (next != Moderation.PUBLISHED && feedback.reportsAvailable(connection)) {
            feedback.processReports(connection, reviewId, administrator, ReportStatus.RESOLVED);
        }
        return true;
    }

    public boolean dismissReports(Connection connection, long id, long version, String administrator)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT review_id FROM product_reviews WHERE review_id = ? AND version = ? FOR UPDATE")) {
            query.setLong(1, id);
            query.setLong(2, version);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return false;
            }
        }
        return feedback.processReports(connection, id, administrator, ReportStatus.DISMISSED) > 0;
    }

    private static void bind(PreparedStatement query, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) query.setObject(index + 1, parameters.get(index));
    }

    public static final class Page {
        public final List<Row> items;
        public final long total;
        public final int number;
        public final long pages;
        public final Map<Long, List<Answer>> answers;
        public final Map<Long, List<Long>> photos;
        public final boolean reportsAvailable;
        public final Map<Long, ReportCounts> reports;

        Page(List<Row> items, long total, int number, long pages, Map<Long, List<Answer>> answers,
                Map<Long, List<Long>> photos, boolean reportsAvailable, Map<Long, ReportCounts> reports) {
            this.items = List.copyOf(items);
            this.total = total;
            this.number = number;
            this.pages = pages;
            this.answers = answers;
            this.photos = photos;
            this.reportsAvailable = reportsAvailable;
            this.reports = reports;
        }
    }

    public static final class Row {
        public final long id;
        public final String productId;
        public final String productCode;
        public final String productName;
        public final String loginId;
        public final int rating;
        public final String content;
        public final LocalDateTime createdAt;
        public final boolean deleted;
        public final long version;
        public final Moderation moderation;
        public final LocalDateTime moderatedAt;

        Row(ResultSet rows) throws SQLException {
            id = rows.getLong("review_id");
            productId = rows.getString("product_id");
            productCode = rows.getString("product_code");
            productName = rows.getString("name");
            loginId = rows.getString("login_id");
            rating = rows.getInt("rating");
            content = rows.getString("content");
            createdAt = rows.getObject("created_at", LocalDateTime.class);
            deleted = rows.getObject("deleted_at") != null;
            version = rows.getLong("version");
            moderation = Moderation.valueOf(rows.getString("moderation_state"));
            moderatedAt = rows.getObject("changed_at", LocalDateTime.class);
        }

        public boolean canChangeTo(Moderation next) {
            return !deleted && moderation.mayChangeTo(next);
        }
    }
}
