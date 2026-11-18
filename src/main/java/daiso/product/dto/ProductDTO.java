package daiso.product.dto;

public class ProductDTO {

    // 필드와 기본 설정
    private String productId;
    private String name;
    private int price;
    private int stockQuantity;
    private String categoryId;
    private String productCode;
    private String brand;
    private String status;
    private String image;
    private String description;
    private double rating;
    private int reviewCount;
    private String sourceKind;
    private String sourceNote;

    // 상품 식별 정보 읽기와 설정
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductCode() {
		return productCode;
	}

    public void setProductCode(String productCode) {
		this.productCode = productCode;
	}

    // 상품명과 브랜드 및 카테고리 정보
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
		return brand;
	}

    public void setBrand(String brand) {
		this.brand = brand;
	}

    public String getCategoryId() {
		return categoryId;
	}

    public void setCategoryId(String categoryId) {
		this.categoryId = categoryId;
	}

    // 가격과 재고 및 판매 상태 정보
    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getStatus() {
		return status;
	}

    public void setStatus(String status) {
		this.status = status;
	}

    // 이미지와 상품 설명 정보
    public String getImage() {
		return image;
	}

    public void setImage(String image) {
		this.image = image;
	}

    public String getDescription() {
		return description;
	}

    public void setDescription(String description) {
		this.description = description;
	}

    // 평점과 리뷰 집계 정보
    public double getRating() {
		return rating;
	}

    public void setRating(double rating) {
		this.rating = rating;
	}

    public int getReviewCount() {
		return reviewCount;
	}

    public void setReviewCount(int reviewCount) {
		this.reviewCount = reviewCount;
	}

    // 상품 데이터 출처 정보
    public String getSourceKind() {
		return sourceKind;
	}

    public void setSourceKind(String sourceKind) {
		this.sourceKind = sourceKind;
	}

    public String getSourceNote() {
		return sourceNote;
	}

    public void setSourceNote(String sourceNote) {
		this.sourceNote = sourceNote;
	}

}
