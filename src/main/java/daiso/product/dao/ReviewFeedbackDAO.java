package daiso.product.dao;

import daiso.product.dto.ReviewFeedback;
import daiso.product.dto.ReviewFeedback.Reaction;
import daiso.product.dto.ReviewFeedback.Reason;
import daiso.product.dto.ReviewFeedback.ReportStatus;
import daiso.product.dto.ReviewFeedback.Tally;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReviewFeedbackDAO {
    private static final String REACTIONS = "product_review_reactions";
    private static final String REPORTS = "product_review_reports";

    public ReviewFeedback load(Connection connection, List<Long> reviews, String member) throws SQLException {
        if (reviews.isEmpty()) return ReviewFeedback.UNAVAILABLE;
        boolean reactionsAvailable = true;
        Map<Long, Map<Reaction, Tally>> tallies = new HashMap<>();
        String sql = "SELECT review_id, reaction_type, COUNT(*), MAX(member_id = ?) FROM " + REACTIONS
                + " WHERE review_id IN (" + markers(reviews) + ") GROUP BY review_id, reaction_type";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, member);
            bindIds(query, reviews, 2);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    tallies.computeIfAbsent(rows.getLong(1), id -> new EnumMap<>(Reaction.class))
                            .put(Reaction.valueOf(rows.getString(2)), new Tally(rows.getLong(3), rows.getBoolean(4)));
                }
            }
        } catch (SQLException error) {
            if (!tableMissing(error)) throw error;
            reactionsAvailable = false;
        }
        return new ReviewFeedback(reactionsAvailable, reportsAvailable(connection), tallies);
    }

    // Serialize feedback and moderation on the review row and read its current visibility.
    public boolean canRespond(Connection connection, long review, String member) throws SQLException {
        String sql = "SELECT rv.review_id FROM product_reviews rv LEFT JOIN product_review_moderation rm "
                + "ON rm.review_id = rv.review_id WHERE rv.review_id = ? AND rv.member_id <> ? "
                + "AND rv.deleted_at IS NULL AND COALESCE(rm.moderation_state, 'PUBLISHED') = 'PUBLISHED' FOR UPDATE";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, review);
            query.setString(2, member);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    public Tally setReaction(Connection connection, long review, String member, Reaction reaction, boolean selected)
            throws SQLException {
        String sql = selected
                ? "INSERT INTO " + REACTIONS + " (review_id, member_id, reaction_type) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE created_at = created_at"
                : "DELETE FROM " + REACTIONS + " WHERE review_id = ? AND member_id = ? AND reaction_type = ?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, review);
            query.setString(2, member);
            query.setString(3, reaction.name());
            query.executeUpdate();
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM " + REACTIONS
                + " WHERE review_id = ? AND reaction_type = ?")) {
            query.setLong(1, review);
            query.setString(2, reaction.name());
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return new Tally(rows.getLong(1), selected);
            }
        }
    }

    public boolean report(Connection connection, long review, String member, Reason reason) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("INSERT INTO " + REPORTS
                + " (review_id, reporter_member_id, reason_code) VALUES (?, ?, ?)")) {
            query.setLong(1, review);
            query.setString(2, member);
            query.setString(3, reason.name());
            query.executeUpdate();
            return true;
        } catch (SQLException error) {
            if (error.getErrorCode() == 1062) return false;
            throw error;
        }
    }

    public boolean reportsAvailable(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM " + REPORTS + " LIMIT 0");
                ResultSet rows = query.executeQuery()) {
            return true;
        } catch (SQLException error) {
            if (tableMissing(error)) return false;
            throw error;
        }
    }

    static boolean tableMissing(SQLException error) {
        return error.getErrorCode() == 1146;
    }

    public Map<Long, ReportCounts> reportCounts(Connection connection, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return Map.of();
        String sql = "SELECT review_id, COUNT(*), SUM(status = 'PENDING') FROM " + REPORTS
                + " WHERE review_id IN (" + markers(ids) + ") GROUP BY review_id";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bindIds(query, ids, 1);
            try (ResultSet rows = query.executeQuery()) {
                Map<Long, ReportCounts> totals = new HashMap<>();
                while (rows.next()) totals.put(rows.getLong(1), new ReportCounts(rows.getLong(2), rows.getLong(3)));
                return totals;
            }
        }
    }

    public List<ReportEntry> reportEntries(Connection connection, long id) throws SQLException {
        String sql = "SELECT reason_code, status, created_at, processed_at FROM " + REPORTS
                + " WHERE review_id = ? ORDER BY created_at DESC, report_id DESC";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, id);
            try (ResultSet rows = query.executeQuery()) {
                List<ReportEntry> reports = new ArrayList<>();
                while (rows.next()) reports.add(new ReportEntry(Reason.valueOf(rows.getString(1)),
                        ReportStatus.valueOf(rows.getString(2)), rows.getObject(3, LocalDateTime.class),
                        rows.getObject(4, LocalDateTime.class)));
                return reports;
            }
        }
    }

    public int processReports(Connection connection, long id, String administrator, ReportStatus status)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("UPDATE " + REPORTS
                + " SET status = ?, processed_by = ?, processed_at = CURRENT_TIMESTAMP(6) "
                + "WHERE review_id = ? AND status = 'PENDING'")) {
            query.setString(1, status.name());
            query.setString(2, administrator);
            query.setLong(3, id);
            return query.executeUpdate();
        }
    }

    private static String markers(List<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private static void bindIds(PreparedStatement query, List<Long> ids, int first) throws SQLException {
        for (int index = 0; index < ids.size(); index++) query.setLong(first + index, ids.get(index));
    }

    public static final class ReportCounts {
        public static final ReportCounts EMPTY = new ReportCounts(0, 0);
        public final long total;
        public final long pending;

        ReportCounts(long total, long pending) {
            this.total = total;
            this.pending = pending;
        }
    }

    public static final class ReportEntry {
        public final Reason reason;
        public final ReportStatus status;
        public final LocalDateTime received;
        public final LocalDateTime processed;

        ReportEntry(Reason reason, ReportStatus status, LocalDateTime received, LocalDateTime processed) {
            this.reason = reason;
            this.status = status;
            this.received = received;
            this.processed = processed;
        }
    }
}
