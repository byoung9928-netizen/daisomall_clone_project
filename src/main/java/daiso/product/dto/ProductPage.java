package daiso.product.dto;

import daiso.product.dto.ProductDTO;
import java.util.List;

// 상품 조회에 사용하는 데이터
public final class ProductPage {
    public final List<ProductDTO> products;
    public final int total;

    public ProductPage(List<ProductDTO> products, int total) {
        this.products = products;
        this.total = total;
    }
}
