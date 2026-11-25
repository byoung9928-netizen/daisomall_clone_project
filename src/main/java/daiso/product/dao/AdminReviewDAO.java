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
    public enum ReportFilter {
        ALL("전체 리뷰", "리뷰", ""), REPORTED("신고된 리뷰", "리뷰", "scoped.total > 0"),
        PENDING("미처리 신고", "신고", "scoped.pending > 0"),
        COMPLETED("처리 완료 신고", "신고", "scoped.completed > 0");

        public final String label;
        public final String unit;
        private final String match;

        ReportFilter(String label, String unit, String match) {
            this.label = label;
            this.unit = unit;
            this.match = match;
        }
    }

    public enum DateField {
        REVIEW("리뷰 작성일", "r.created_at"), RECEIVED("신고 접수일", "rr.created_at"),
        PROCESSED("신고 처리일", "rr.processed_at");

        public final String label;
        private final String column;

        DateField(String label, String column) {
            this.label = label;
            this.column = column;
        }
    }

    public enum Sort {
        NEWEST("최신 작성순", ""), OLDEST("오래된 작성순", ""),
        HIGHEST("별점 높은순", "r.rating DESC, "), LOWEST("별점 낮은순", "r.rating ASC, "),
        MOST_REPORTED("신고 많은순", "COALESCE(%s.total, 0) DESC, "),
        MOST_PENDING("미처리 신고 우선순", "COALESCE(%s.pending, 0) DESC, "),
        MOST_REACTED("반응 많은순", "COALESCE(reactions.total, 0) DESC, ");

        public final String label;
        private final String order;

        Sort(String label, String order) {
            this.label = label;
            this.order = order;
        }

        private boolean usesReports() { return this == MOST_REPORTED || this == MOST_PENDING; }

        private String orderBy(String reportAlias, boolean reportsAvailable) {
            if (this == OLDEST) return "r.created_at ASC, r.review_id DESC";
            String prefix = usesReports() ? (reportsAvailable ? String.format(order, reportAlias) : "") : order;
            return prefix + "r.created_at DESC, r.review_id DESC";
        }
    }

    public static final class Filter {
        public final String keyword;
        public final int rating;
        public final State state;
        public final ReportFilter reports;
        public final DateField dateField;
        public final LocalDateTime start;
        public final LocalDateTime endExclusive;
        public final Sort sort;
        public final String reason;
        public final boolean photo;
        public final boolean reaction;
        public final String analysisProduct;

        public Filter(String keyword, int rating, State state, ReportFilter reports, DateField dateField,
                LocalDateTime start, LocalDateTime endExclusive, Sort sort, String reason,
                boolean photo, boolean reaction, String analysisProduct) {
            this.keyword = keyword;
            this.rating = rating;
            this.state = state;
            this.reports = reports;
            this.dateField = dateField;
            this.start = start;
            this.endExclusive = endExclusive;
            this.sort = sort;
            this.reason = reason;
            this.photo = photo;
            this.reaction = reaction;
            this.analysisProduct = analysisProduct;
        }
    }

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

    private static final String REVIEW_SOURCE = " FROM product_reviews r ";
    private static final String SEARCH_JOINS = " JOIN products p ON p.product_id = r.product_id "
            + "JOIN members m ON m.member_id = r.member_id ";
    private static final String MODERATION_JOIN = " LEFT JOIN product_review_moderation rm ON rm.review_id = r.review_id ";
    private static final String REPORT_TOTALS = "SELECT rr.review_id, COUNT(*) AS total, "
            + "SUM(rr.status = 'PENDING') AS pending, SUM(rr.status IN ('RESOLVED', 'DISMISSED')) AS completed "
            + "FROM product_review_reports rr ";
    private static final String REVIEW_COLUMNS = "SELECT r.review_id, r.product_id, p.product_code, p.name, "
            + "m.login_id, r.rating, r.content, r.created_at, r.deleted_at, r.version, rm.changed_at, "
            + "COALESCE(rm.moderation_state, 'PUBLISHED') AS moderation_state";

    public Page list(Connection connection, Filter filter, int requestedPage, long selectedReview) throws SQLException {
        boolean reportsAvailable = feedback.reportsAvailable(connection);
        boolean reportDate = filter.dateField != DateField.REVIEW;
        boolean dateScoped = reportDate && (filter.start != null || filter.dateField == DateField.PROCESSED);
        boolean scopedReports = dateScoped || !filter.reason.isEmpty();
        boolean photosReady = tableReady(connection, "product_review_photos");
        boolean reactionsReady = tableReady(connection, "product_review_reactions");
        String reportWhere = "";
        List<Object> dates = new ArrayList<>();
        if (dateScoped) {
            if (filter.start != null) {
                reportWhere = " WHERE " + filter.dateField.column + " >= ? AND " + filter.dateField.column + " < ? ";
                dates.add(filter.start);
                dates.add(filter.endExclusive);
            } else {
                reportWhere = " WHERE rr.processed_at IS NOT NULL ";
            }
        }
        if (!filter.reason.isEmpty()) {
            reportWhere += reportWhere.isEmpty() ? " WHERE rr.reason_code = ? " : " AND rr.reason_code = ? ";
            dates.add(filter.reason);
        }
        String scopedJoin = reportsAvailable ? reportJoin("scoped", reportWhere) : "";
        String membership = filter.reports == ReportFilter.ALL ? "TRUE"
                : reportsAvailable ? filter.reports.match : "FALSE";
        List<Object> parameters = new ArrayList<>();
        String baseWhere = reviewWhere(filter, reportDate, reportsAvailable, parameters);
        if (!reportDate && !filter.reason.isEmpty()) baseWhere += reportsAvailable
                ? " AND scoped.review_id IS NOT NULL" : " AND FALSE";
        if (filter.photo) baseWhere += photosReady
                ? " AND EXISTS (SELECT 1 FROM product_review_photos ph WHERE ph.review_id = r.review_id)" : " AND FALSE";
        if (filter.reaction) baseWhere += reactionsReady
                ? " AND EXISTS (SELECT 1 FROM product_review_reactions rea WHERE rea.review_id = r.review_id)" : " AND FALSE";

        String aggregates = reportsAvailable ? "COUNT(scoped.review_id), "
                + "COALESCE(SUM(scoped.pending), 0), COALESCE(SUM(scoped.completed), 0)" : "0, 0, 0";
        String summarySource = REVIEW_SOURCE + (filter.keyword.isEmpty() ? "" : SEARCH_JOINS)
                + (filter.state == State.ALL || filter.state == State.DELETED ? "" : MODERATION_JOIN);
        String summarySql = "SELECT COUNT(*), " + aggregates + ", COUNT(CASE WHEN " + membership + " THEN 1 END)"
                + summarySource + scopedJoin + baseWhere;
        Map<ReportFilter, Long> summary;
        long total;
        try (PreparedStatement query = connection.prepareStatement(summarySql)) {
            bind(query, datesFor(reportsAvailable, dates, parameters));
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                summary = Map.of(ReportFilter.ALL, rows.getLong(1), ReportFilter.REPORTED, rows.getLong(2),
                        ReportFilter.PENDING, rows.getLong(3), ReportFilter.COMPLETED, rows.getLong(4));
                total = rows.getLong(5);
            }
        }

        int limit = 20;
        int pages = (int) Math.min(Integer.MAX_VALUE, Math.max(1, (total + limit - 1) / limit));
        int page = Math.min(requestedPage, pages);
        List<Row> items = new ArrayList<>();
        if (total > 0) {
            boolean needsScope = reportsAvailable && (reportDate || !filter.reason.isEmpty() || filter.reports != ReportFilter.ALL);
            boolean needsTotals = reportsAvailable && filter.sort.usesReports() && (!needsScope || scopedReports);
            boolean reactionSort = filter.sort == Sort.MOST_REACTED && reactionsReady;
            Sort sort = filter.sort == Sort.MOST_REACTED && !reactionSort ? Sort.NEWEST : filter.sort;
            String listSql = REVIEW_COLUMNS + REVIEW_SOURCE + SEARCH_JOINS + MODERATION_JOIN
                    + (needsScope ? scopedJoin : "")
                    + (needsTotals ? reportJoin("lifetime", "") : "")
                    + (reactionSort ? " LEFT JOIN (SELECT review_id, COUNT(*) AS total FROM product_review_reactions "
                            + "GROUP BY review_id) reactions ON reactions.review_id = r.review_id " : "")
                    + baseWhere + " AND " + membership
                    + " ORDER BY " + sort.orderBy(needsTotals ? "lifetime" : "scoped", reportsAvailable)
                    + " LIMIT ? OFFSET ?";
            List<Object> listParameters = datesFor(needsScope, dates, parameters);
            try (PreparedStatement query = connection.prepareStatement(listSql)) {
                bind(query, listParameters);
                query.setInt(listParameters.size() + 1, limit);
                query.setLong(listParameters.size() + 2, (page - 1L) * limit);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) items.add(new Row(rows));
                }
            }
        }
        List<Long> ids = items.stream().map(item -> item.id).collect(Collectors.toList());
        List<Long> detail = ids.contains(selectedReview) ? List.of(selectedReview) : List.of();
        boolean attachmentsAvailable = !detail.isEmpty() && extras.available(connection);
        return new Page(items, total, page, pages, summary,
                attachmentsAvailable ? extras.answers(connection, detail) : Map.of(),
                attachmentsAvailable ? extras.photos(connection, detail) : Map.of(), reportsAvailable,
                reportsAvailable ? feedback.reportCounts(connection, ids) : Map.of(),
                photosReady, reactionsReady);
    }

    public boolean tableReady(Connection connection, String table) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    public Analysis analysis(Connection connection, String id, Page page) throws SQLException {
        String sql = "WITH target AS (SELECT product_id, name FROM products WHERE product_id = ? OR product_code = ? "
                + "ORDER BY product_id = ? DESC, product_id LIMIT 1), published AS ("
                + "SELECT rv.review_id, rv.rating FROM product_reviews rv JOIN target t ON t.product_id = rv.product_id "
                + "WHERE rv.deleted_at IS NULL" + ReviewVisibility.PUBLISHED + "), "
                + "summary AS (SELECT COUNT(*) total, AVG(rating) average, SUM(rating=1) star1, SUM(rating=2) star2, "
                + "SUM(rating=3) star3, SUM(rating=4) star4, SUM(rating=5) star5 FROM published) "
                + "SELECT t.name, summary.*, "
                + (page.photosAvailable ? "(SELECT COUNT(DISTINCT ph.review_id) FROM product_review_photos ph JOIN published p "
                        + "ON p.review_id=ph.review_id)" : "0") + " photos, "
                + (page.reactionsAvailable ? "(SELECT COUNT(*) FROM product_review_reactions rea JOIN published p "
                        + "ON p.review_id=rea.review_id)" : "0") + " reactions, "
                + (page.reportsAvailable ? "(SELECT COUNT(DISTINCT rr.review_id) FROM product_review_reports rr JOIN product_reviews r "
                        + "ON r.review_id=rr.review_id WHERE r.product_id=t.product_id)" : "0") + " reported, "
                + (page.reportsAvailable ? "(SELECT COUNT(*) FROM product_review_reports rr JOIN product_reviews r ON r.review_id=rr.review_id "
                        + "WHERE r.product_id=t.product_id AND rr.status='PENDING')" : "0") + " pending "
                + "FROM target t CROSS JOIN summary";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, id);
            query.setString(2, id);
            query.setString(3, id);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? new Analysis(rows) : null; }
        }
    }

    public String lockDecisionState(Connection connection, long reviewId) throws SQLException {
        String sql = "SELECT IF(r.deleted_at IS NULL, COALESCE(rm.moderation_state, 'PUBLISHED'), 'AUTHOR_DELETED')"
                + REVIEW_SOURCE + MODERATION_JOIN + " WHERE r.review_id=? FOR UPDATE";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, reviewId);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public void recordDecision(Connection connection, long reviewId, String manager, String action,
            String before, String after, String reason) throws SQLException {
        String sql = "INSERT INTO product_review_admin_decisions "
                + "(review_id, admin_member_id, action, before_state, after_state, reason) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, reviewId);
            query.setString(2, manager);
            query.setString(3, action);
            query.setString(4, before);
            query.setString(5, after);
            query.setString(6, reason);
            query.executeUpdate();
        }
    }

    public List<Decision> decisions(Connection connection, long reviewId) throws SQLException {
        String sql = "SELECT admin_member_id, action, before_state, after_state, reason, created_at "
                + "FROM product_review_admin_decisions WHERE review_id=? ORDER BY decision_id DESC LIMIT 30";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, reviewId);
            try (ResultSet rows = query.executeQuery()) {
                List<Decision> result = new ArrayList<>();
                while (rows.next()) result.add(new Decision(rows));
                return result;
            }
        }
    }

    public static final class Analysis {
        public final String name;
        public final long total;
        public final java.math.BigDecimal average;
        public final long[] stars = new long[5];
        public final long photos;
        public final long reactions;
        public final long reported;
        public final long pending;

        Analysis(ResultSet rows) throws SQLException {
            name = rows.getString("name");
            total = rows.getLong("total");
            average = rows.getBigDecimal("average");
            for (int i = 0; i < stars.length; i++) stars[i] = rows.getLong("star" + (i + 1));
            photos = rows.getLong("photos");
            reactions = rows.getLong("reactions");
            reported = rows.getLong("reported");
            pending = rows.getLong("pending");
        }
    }

    public static final class Decision {
        public final String manager;
        public final String action;
        public final String before;
        public final String after;
        public final String reason;
        public final LocalDateTime date;

        Decision(ResultSet rows) throws SQLException {
            manager = rows.getString(1);
            action = rows.getString(2);
            before = rows.getString(3);
            after = rows.getString(4);
            reason = rows.getString(5);
            date = rows.getObject(6, LocalDateTime.class);
        }
    }

    private static String reportJoin(String alias, String where) {
        return " LEFT JOIN (" + REPORT_TOTALS + where + "GROUP BY rr.review_id) " + alias
                + " ON " + alias + ".review_id = r.review_id ";
    }

    private static List<Object> datesFor(boolean includeDates, List<Object> dates, List<Object> parameters) {
        if (!includeDates || dates.isEmpty()) return parameters;
        List<Object> values = new ArrayList<>(dates);
        values.addAll(parameters);
        return values;
    }

    private static String reviewWhere(Filter filter, boolean reportDate, boolean reportsAvailable, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (!filter.keyword.isEmpty()) {
            where.append(" AND (LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0 "
                    + "OR LOCATE(?, m.login_id) > 0 OR LOCATE(?, r.content) > 0)");
            for (int field = 0; field < 4; field++) params.add(filter.keyword);
        }
        if (filter.rating > 0) {
            where.append(" AND r.rating = ?");
            params.add(filter.rating);
        }
        if (filter.state == State.DELETED) where.append(" AND r.deleted_at IS NOT NULL");
        if (filter.state == State.ACTIVE || filter.state == State.HIDDEN || filter.state == State.REMOVED) {
            where.append(" AND r.deleted_at IS NULL AND COALESCE(rm.moderation_state, 'PUBLISHED') = ?");
            params.add(filter.state == State.ACTIVE ? Moderation.PUBLISHED.name()
                    : filter.state == State.HIDDEN ? Moderation.HIDDEN.name() : Moderation.DELETED.name());
        }
        if (reportDate) {
            where.append(reportsAvailable ? " AND scoped.review_id IS NOT NULL" : " AND FALSE");
        } else if (filter.start != null) {
            where.append(" AND ").append(filter.dateField.column).append(" >= ? AND ")
                    .append(filter.dateField.column).append(" < ?");
            params.add(filter.start);
            params.add(filter.endExclusive);
        }
        return where.toString();
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
        public final int pages;
        public final Map<ReportFilter, Long> summary;
        public final Map<Long, List<Answer>> answers;
        public final Map<Long, List<Long>> photos;
        public final boolean reportsAvailable;
        public final boolean photosAvailable;
        public final boolean reactionsAvailable;
        public final Map<Long, ReportCounts> reports;

        Page(List<Row> items, long total, int number, int pages, Map<ReportFilter, Long> summary,
                Map<Long, List<Answer>> answers,
                Map<Long, List<Long>> photos, boolean reportsAvailable, Map<Long, ReportCounts> reports, boolean photosAvailable, boolean reactionsAvailable) {
            this.items = List.copyOf(items);
            this.total = total;
            this.number = number;
            this.pages = pages;
            this.summary = summary;
            this.answers = answers;
            this.photos = photos;
            this.reportsAvailable = reportsAvailable;
            this.reports = reports;
            this.photosAvailable = photosAvailable;
            this.reactionsAvailable = reactionsAvailable;
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
