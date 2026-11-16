package daiso.product.controller;

public class ProductDTO {

    private String productId;
    private String name;
    private int price;
    private int stockQuantity;

    // 상품번호
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    // 상품명
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // 가격
    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    // 재고
    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }
}