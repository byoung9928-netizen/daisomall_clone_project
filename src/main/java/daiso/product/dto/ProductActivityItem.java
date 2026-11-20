package daiso.product.dto;

import daiso.product.controller.ProductDTO;

public final class ProductActivityItem {
    public final ProductDTO product;
    public final long purchases;

    public ProductActivityItem(ProductDTO product, long purchases) {
        this.product = product;
        this.purchases = purchases;
    }
}
