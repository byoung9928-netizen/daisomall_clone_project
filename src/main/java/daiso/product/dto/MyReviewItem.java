package daiso.product.dto;

import daiso.product.controller.ProductDTO;
import java.time.LocalDateTime;

public final class MyReviewItem {
    public final ProductDTO product;
    public final LocalDateTime date;
    public final int rating;
    public final String content;
    public final String state;
    public final boolean visible;
    public final String purchaseType;
    public final boolean refunded;

    public MyReviewItem(ProductDTO product, LocalDateTime date, int rating,
            String content, String state, boolean visible, String purchaseType, boolean refunded) {
        this.product = product;
        this.date = date;
        this.rating = rating;
        this.content = content;
        this.state = state;
        this.visible = visible;
        this.purchaseType = purchaseType;
        this.refunded = refunded;
    }
}
