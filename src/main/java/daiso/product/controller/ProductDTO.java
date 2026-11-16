package daiso.product.controller;

public class ProductDTO {

    private String productId;
    private String name;
    private int price;
    private int stockQuantity;

    String categoryId;
    String productCode;
    String brand;
    String status;
    String image;
    String description;
    double rating;
    int reviewCount;
    String sourceKind;
    String sourceNote;

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

	public String getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(String categoryId) {
		this.categoryId = categoryId;
	}

	public String getProductCode() {
		return productCode;
	}

	public void setProductCode(String productCode) {
		this.productCode = productCode;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

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