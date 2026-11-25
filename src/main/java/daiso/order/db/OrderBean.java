package daiso.order.db;

import java.util.List;

import daiso.cart.db.CartBean;
import java.sql.Timestamp;

public class OrderBean {
	 	private String orderNo;
	    private String memberId;
	    private String checkoutToken;

	    private String recipient;
	    private String phone;
	    private String postcode;
	    private String address1;
	    private String address2;
	    private String deliveryMessage;

	    private int subtotalAmount;
	    private int usedPoints;
	    private int shippingFee;
	    private int totalAmount;
	    
	    private List<CartBean> items;
	    
	    //관리자
	    private long orderId;
	    private String status;
	    private String paymentStatus;
	    private Timestamp createdAt;
	    private String productSummary;
	    
	    //배송
	    private String carrierId;
	    private String trackingNumber;
	    
	    
		public int getUsedPoints() {
			return usedPoints;
		}
		public void setUsedPoints(int usedPoints) {
			this.usedPoints = usedPoints;
		}
		public String getCarrierId() {
			return carrierId;
		}
		public void setCarrierId(String carrierId) {
			this.carrierId = carrierId;
		}
		public String getTrackingNumber() {
			return trackingNumber;
		}
		public void setTrackingNumber(String trackingNumber) {
			this.trackingNumber = trackingNumber;
		}
		public long getOrderId() {
			return orderId;
		}
		public void setOrderId(long orderId) {
			this.orderId = orderId;
		}
		public String getStatus() {
			return status;
		}
		public void setStatus(String status) {
			this.status = status;
		}
		public String getPaymentStatus() {
			return paymentStatus;
		}
		public void setPaymentStatus(String paymentStatus) {
			this.paymentStatus = paymentStatus;
		}
		public Timestamp getCreatedAt() {
			return createdAt;
		}
		public void setCreatedAt(Timestamp createdAt) {
			this.createdAt = createdAt;
		}
		public String getProductSummary() {
			return productSummary;
		}
		public void setProductSummary(String productSummary) {
			this.productSummary = productSummary;
		}
		public String getOrderNo() {
			return orderNo;
		}
		public void setOrderNo(String orderNo) {
			this.orderNo = orderNo;
		}
		public String getMemberId() {
			return memberId;
		}
		public void setMemberId(String memberId) {
			this.memberId = memberId;
		}
		public String getCheckoutToken() {
			return checkoutToken;
		}
		public void setCheckoutToken(String checkoutToken) {
			this.checkoutToken = checkoutToken;
		}
		public String getRecipient() {
			return recipient;
		}
		public void setRecipient(String recipient) {
			this.recipient = recipient;
		}
		public String getPhone() {
			return phone;
		}
		public void setPhone(String phone) {
			this.phone = phone;
		}
		public String getPostcode() {
			return postcode;
		}
		public void setPostcode(String postcode) {
			this.postcode = postcode;
		}
		public String getAddress1() {
			return address1;
		}
		public void setAddress1(String address1) {
			this.address1 = address1;
		}
		public String getAddress2() {
			return address2;
		}
		public void setAddress2(String address2) {
			this.address2 = address2;
		}
		public String getDeliveryMessage() {
			return deliveryMessage;
		}
		public void setDeliveryMessage(String deliveryMessage) {
			this.deliveryMessage = deliveryMessage;
		}
		public int getSubtotalAmount() {
			return subtotalAmount;
		}
		public void setSubtotalAmount(int subtotalAmount) {
			this.subtotalAmount = subtotalAmount;
		}
		public int getShippingFee() {
			return shippingFee;
		}
		public void setShippingFee(int shippingFee) {
			this.shippingFee = shippingFee;
		}
		public int getTotalAmount() {
			return totalAmount;
		}
		public void setTotalAmount(int totalAmount) {
			this.totalAmount = totalAmount;
		}
		public List<CartBean> getItems() {
		    return items;
		}

		public void setItems(List<CartBean> items) {
		    this.items = items;
		}

}
