package daiso.product.service;

import daiso.product.dao.ProductConnectionFactory;
import daiso.product.dao.ProductReviewDAO;
import daiso.product.dao.ProductReviewDAO.Sort;
import daiso.product.dto.ProductReview;
import daiso.product.dto.ProductReviewPage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.OptionalLong;
import javax.naming.NamingException;

public final class ProductReviewService {
    private static final int PAGE_SIZE = 10;
    private final ProductReviewDAO dao = new ProductReviewDAO();

    public enum Result { SAVED, PURCHASE_REQUIRED, DUPLICATE, STALE_OR_UNOWNED }

    public ProductReviewPage page(String productId, String memberId, Sort sort, int requestedPage)
            throws SQLException, NamingException {
        try (Connection connection = ProductConnectionFactory.openQueryConnection()) {
            ProductReviewDAO.Summary summary = dao.summarize(connection, productId);
            long pages = Math.max(1, (summary.count + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = (int) Math.min(requestedPage, pages);
            ProductReview ownReview = memberId == null ? null : dao.findOwn(connection, productId, memberId);
            boolean canWrite = memberId != null && ownReview == null
                    && dao.findPaidItem(connection, memberId, productId, false).isPresent();
            ProductReviewPage result = new ProductReviewPage(
                    dao.list(connection, productId, memberId, sort, PAGE_SIZE, (page - 1L) * PAGE_SIZE),
                    ownReview, canWrite, summary.count, summary.average, page, pages);
            connection.commit();
            return result;
        }
    }

    public Result create(String productId, String memberId, int rating, String content)
            throws SQLException, NamingException {
        String cleaned = validate(rating, content);
        try {
            return write(connection -> {
                OptionalLong paidItem = dao.findPaidItem(connection, memberId, productId, true);
                if (paidItem.isEmpty()) return Result.PURCHASE_REQUIRED;
                dao.insert(connection, productId, memberId, paidItem.getAsLong(), rating, cleaned);
                return Result.SAVED;
            });
        } catch (SQLException error) {
            // 신규 행의 유일한 사용자 지정 중복 제약은 활성 리뷰 키다.
            if (error.getErrorCode() == 1062) return Result.DUPLICATE;
            throw error;
        }
    }

    public Result update(String productId, String memberId, long id, long version, int rating, String content)
            throws SQLException, NamingException {
        String cleaned = validate(rating, content);
        return write(connection -> dao.changeOwn(connection, productId, memberId, id, version, false, rating, cleaned)
                ? Result.SAVED : Result.STALE_OR_UNOWNED);
    }

    public Result delete(String productId, String memberId, long id, long version)
            throws SQLException, NamingException {
        return write(connection -> dao.changeOwn(connection, productId, memberId, id, version, true, 0, null)
                ? Result.SAVED : Result.STALE_OR_UNOWNED);
    }

    private Result write(Change change) throws SQLException, NamingException {
        try (Connection connection = ProductConnectionFactory.openWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                Result result = change.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }

    private static String validate(int rating, String text) {
        String content = text == null ? "" : text.strip();
        if (rating < 1 || rating > 5 || content.isBlank()
                || content.codePointCount(0, content.length()) > 2000) {
            throw new IllegalArgumentException("별점은 1~5점, 내용은 1~2,000자로 입력해 주세요.");
        }
        return content;
    }

    @FunctionalInterface
    private interface Change {
        Result run(Connection connection) throws SQLException;
    }
}
