package daiso.product.dto;

import daiso.product.controller.ProductDTO;
import java.time.LocalDateTime;

public final class MyReviewItem {
    public final ProductDTO product;
    public final LocalDateTime date;
    public final long reviewId;
    public final int rating;
    public final String content;
    public final String state;
    public final boolean visible;

    public MyReviewItem(ProductDTO product, LocalDateTime date, long reviewId, int rating,
            String content, String state, boolean visible) {
        this.product = product;
        this.date = date;
        this.reviewId = reviewId;
        this.rating = rating;
        this.content = content;
        this.state = state;
        this.visible = visible;
    }
}
