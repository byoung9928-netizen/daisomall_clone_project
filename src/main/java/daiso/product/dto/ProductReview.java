package daiso.product.dto;

import java.time.LocalDateTime;

public final class ProductReview {
    public final long id;
    public final int rating;
    public final String content;
    public final LocalDateTime createdAt;
    public final boolean owner;
    public final long version;

    public ProductReview(long id, int rating, String content, LocalDateTime createdAt,
            boolean owner, long version) {
        this.id = id;
        this.rating = rating;
        this.content = content;
        this.createdAt = createdAt;
        this.owner = owner;
        this.version = version;
    }
}
