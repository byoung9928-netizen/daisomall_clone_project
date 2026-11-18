package daiso.product.dto;

import java.time.LocalDateTime;
import daiso.product.controller.ProductDTO;

/** 찜 목록의 상품 정보와 등록 시각을 전달합니다. */
public final class WishlistItem {
    private final ProductDTO product;
    private final LocalDateTime createdAt;

    public WishlistItem(ProductDTO product, LocalDateTime createdAt) {
        this.product = product;
        this.createdAt = createdAt;
    }

    public ProductDTO getProduct() { return product; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
