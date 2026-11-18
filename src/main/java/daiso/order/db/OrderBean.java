package daiso.order.db;

import java.util.List;

import daiso.cart.db.CartBean;

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
	    private int shippingFee;
	    private int totalAmount;
	    
	    private List<CartBean> items;
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
