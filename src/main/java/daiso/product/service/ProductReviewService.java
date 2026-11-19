package daiso.product.service;

import daiso.product.dao.ProductConnectionFactory;
import daiso.product.dao.ProductReviewDAO;
import daiso.product.dao.ProductReviewDAO.Sort;
import daiso.product.dao.ReviewExtrasDAO;
import daiso.product.dto.ProductReview;
import daiso.product.dto.ProductReviewPage;
import daiso.product.dto.ReviewExtras;
import daiso.product.dto.ReviewExtras.Answer;
import daiso.product.dto.ReviewExtras.Submission;
import daiso.product.dto.ReviewQuestion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import javax.naming.NamingException;

public final class ProductReviewService {
    private static final int PAGE_SIZE = 10;
    private final ProductReviewDAO dao = new ProductReviewDAO();
    private final ReviewExtrasDAO extras = new ReviewExtrasDAO();

    public enum Result { SAVED, PURCHASE_REQUIRED, DUPLICATE, STALE_OR_UNOWNED, EXTRAS_UNAVAILABLE }

    public ProductReviewPage page(String productId, String memberId, Sort sort, int requestedPage)
            throws SQLException, NamingException {
        try (Connection connection = ProductConnectionFactory.openQueryConnection()) {
            ProductReviewDAO.Summary summary = dao.summarize(connection, productId);
            long pages = Math.max(1, (summary.count + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = (int) Math.min(requestedPage, pages);
            ProductReview ownReview = memberId == null ? null : dao.findOwn(connection, productId, memberId);
            boolean canWrite = memberId != null && ownReview == null
                    && dao.findPaidItem(connection, memberId, productId, false).isPresent();
            List<ProductReview> items = dao.list(connection, productId, memberId, sort, PAGE_SIZE, (page - 1L) * PAGE_SIZE);
            List<Long> ids = items.stream().map(review -> review.id).collect(Collectors.toList());
            if (ownReview != null && !ids.contains(ownReview.id)) ids.add(ownReview.id);
            boolean editing = canWrite || ownReview != null;
            boolean available = (!ids.isEmpty() || editing) && extras.available(connection);
            ReviewExtras details = new ReviewExtras(available,
                    available && editing ? questions(connection, productId) : List.of(),
                    available ? extras.answers(connection, ids) : Map.of(),
                    available ? extras.photos(connection, ids) : Map.of());
            ProductReviewPage result = new ProductReviewPage(
                    items, ownReview, canWrite, summary.count, summary.average, page, pages, details);
            connection.commit();
            return result;
        }
    }

    public Result create(String productId, String memberId, int rating, String content, Submission submission)
            throws SQLException, NamingException {
        String cleaned = validate(rating, content);
        try {
            return write(connection -> {
                OptionalLong paidItem = dao.findPaidItem(connection, memberId, productId, true);
                if (paidItem.isEmpty()) return Result.PURCHASE_REQUIRED;
                if (submission != null && !extras.available(connection)) return Result.EXTRAS_UNAVAILABLE;
                long id = dao.insert(connection, productId, memberId, paidItem.getAsLong(), rating, cleaned);
                if (submission != null) saveExtras(connection, productId, id, submission);
                return Result.SAVED;
            });
        } catch (SQLException error) {
            // 신규 행의 유일한 사용자 지정 중복 제약은 활성 리뷰 키다.
            if (error.getErrorCode() == 1062) return Result.DUPLICATE;
            throw error;
        }
    }

    public Result update(String productId, String memberId, long id, long version,
            int rating, String content, Submission submission) throws SQLException, NamingException {
        String cleaned = validate(rating, content);
        return write(connection -> {
            if (submission != null && !extras.available(connection)) return Result.EXTRAS_UNAVAILABLE;
            if (!dao.changeOwn(connection, productId, memberId, id, version, false, rating, cleaned)) {
                return Result.STALE_OR_UNOWNED;
            }
            if (submission != null) saveExtras(connection, productId, id, submission);
            return Result.SAVED;
        });
    }

    public Result delete(String productId, String memberId, long id, long version)
            throws SQLException, NamingException {
        return write(connection -> {
            if (!dao.changeOwn(connection, productId, memberId, id, version, true, 0, null)) {
                return Result.STALE_OR_UNOWNED;
            }
            if (extras.available(connection)) extras.removeAllPhotos(connection, id);
            return Result.SAVED;
        });
    }

    public byte[] photo(long id) throws SQLException, NamingException {
        try (Connection connection = ProductConnectionFactory.openQueryConnection()) {
            byte[] photo = extras.available(connection) ? extras.visiblePhoto(connection, id) : null;
            connection.commit();
            return photo;
        }
    }

    private List<ReviewQuestion> questions(Connection connection, String productId) throws SQLException {
        return ReviewQuestions.forPath(extras.categoryPath(connection, productId));
    }

    private void saveExtras(Connection connection, String productId, long id, Submission submission) throws SQLException {
        List<Answer> answers = new ArrayList<>();
        for (ReviewQuestion question : questions(connection, productId)) {
            Integer value = submission.answers.get(question.key);
            if (value == null) continue;
            if (value < 1 || value > question.options.size()) {
                throw new IllegalArgumentException("선택한 리뷰 답변을 확인해 주세요.");
            }
            answers.add(new Answer(question.key, question.label, value, question.options.get(value - 1)));
        }
        if (answers.size() != submission.answers.size()) {
            throw new IllegalArgumentException("상품 분류가 변경되었거나 답변이 올바르지 않습니다. 리뷰 화면을 다시 열어 주세요.");
        }
        List<Long> existing = extras.photos(connection, List.of(id)).getOrDefault(id, List.of());
        if (!existing.containsAll(submission.removedPhotoIds)
                || existing.size() - submission.removedPhotoIds.size() + submission.photos.size() > Submission.MAX_PHOTOS) {
            throw new IllegalArgumentException("삭제할 사진과 첨부 사진 수를 확인해 주세요. 최대 10장까지 저장할 수 있습니다.");
        }
        extras.replaceAnswers(connection, id, answers);
        extras.removePhotos(connection, id, submission.removedPhotoIds);
        extras.addPhotos(connection, id, submission.photos);
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
