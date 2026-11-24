package daiso.product.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ProductReviewPage {
    public final List<ProductReview> items;
    public final ProductReview ownReview;
    public final boolean canWrite;
    public final long total;
    public final BigDecimal average;
    public final int page;
    public final long totalPages;
    public final ReviewExtras extras;
    public final ReviewFeedback feedback;

    public ProductReviewPage(List<ProductReview> items, ProductReview ownReview, boolean canWrite,
            long total, BigDecimal average, int page, long totalPages, ReviewExtras extras, ReviewFeedback feedback) {
        this.items = List.copyOf(items);
        this.ownReview = ownReview;
        this.canWrite = canWrite;
        this.total = total;
        this.average = average;
        this.page = page;
        this.totalPages = totalPages;
        this.extras = extras;
        this.feedback = feedback;
    }
}
