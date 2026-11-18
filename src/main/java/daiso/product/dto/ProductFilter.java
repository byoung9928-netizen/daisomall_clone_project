package daiso.product.dto;

// 상품 조회에 사용하는 데이터
public final class ProductFilter {
    public String category = "";
    public String keyword = "";
    public String brand = "";
    public String status = "";
    public String sort = "recommended";
    public Integer maxPrice;
    public boolean available;
    public boolean lowStock;
    public boolean topRated;
    public int limit;
    public int offset;
}
