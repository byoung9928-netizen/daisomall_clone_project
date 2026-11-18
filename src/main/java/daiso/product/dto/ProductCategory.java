package daiso.product.dto;

// 상품 조회에 사용하는 데이터
public final class ProductCategory {
    public final String id;
    public final String parentId;
    public final String name;
    public final int level;
    public final int displayOrder;

    public ProductCategory(String id, String parentId, String name, int level, int displayOrder) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.level = level;
        this.displayOrder = displayOrder;
    }
}
