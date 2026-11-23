package daiso.cart.db;


public class CartBean {
	private long cartItemId;      // 장바구니 항목 번호
	private String productId;     // 상품 번호
	private String productName;   // 상품명
	private int price;            // 개당 가격
	private int quantity;         // 수량
	private String imageUrl;      // 상품 이미지 경로
	private String productStatus; // 판매 상태
	private int stockQuantity;    // 현재 재고
	
	private String storeId;//매장 id
	private String storeName;//매장 이름
	
	public String getStoreId() {
		return storeId;
	}
	public void setStoreId(String storeId) {
		this.storeId = storeId;
	}
	public String getStoreName() {
		return storeName;
	}
	public void setStoreName(String storeName) {
		this.storeName = storeName;
	}
	public long getItemTotal() {
		return (long)price*quantity;
	}
	public long getCartItemId() {
		return cartItemId;
	}
	public void setCartItemId(long cartItemId) {
		this.cartItemId = cartItemId;
	}
	public String getProductId() {
		return productId;
	}
	public void setProductId(String productId) {
		this.productId = productId;
	}
	public String getProductName() {
		return productName;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}
	public int getPrice() {
		return price;
	}
	public void setPrice(int price) {
		this.price = price;
	}
	public int getQuantity() {
		return quantity;
	}
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	public String getImageUrl() {
		return imageUrl;
	}
	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}
	public String getProductStatus() {
		return productStatus;
	}
	public void setProductStatus(String productStatus) {
		this.productStatus = productStatus;
	}
	public int getStockQuantity() {
		return stockQuantity;
	}
	public void setStockQuantity(int stockQuantity) {
		this.stockQuantity = stockQuantity;
	}
	
    public static boolean isTemporarilySoldOut(String status, int stockQuantity) {
        return "SOLD_OUT".equals(status) || ("AVAILABLE".equals(status) && stockQuantity == 0);
    }

    public boolean isTemporarilySoldOut() {
        return isTemporarilySoldOut(productStatus, stockQuantity);
    }

    public boolean isPurchasable() {
		return "AVAILABLE".equals(productStatus)
				&& quantity >=1
				&& quantity<=99
				&& stockQuantity >= quantity;
	}
	
}
