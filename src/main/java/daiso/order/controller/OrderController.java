package daiso.order.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import daiso.cart.db.CartBean;
import daiso.cart.db.CartDAO;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.order.db.DeliveryAddressDAO;
import daiso.product.dao.StoreInventoryDAO.Location;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import com.google.gson.JsonObject;

@DaisoController
public class OrderController {
    // 기존 orderForm()을 이 메서드로 교체합니다.
    // ===== 매장픽업 주문서: 선택 상품을 로그인 회원의 장바구니에서 다시 조회 =====
    @DaisoGetMapping("/order/pickup/form")
    public String pickupOrderForm(HttpServletRequest request, HttpServletResponse response)
            throws java.sql.SQLException, javax.naming.NamingException {
        javax.servlet.http.HttpSession session = request.getSession(false);
        String memberId = session == null ? null : (String) session.getAttribute("member_id");
        if (memberId == null || memberId.isBlank()) return "redirect:/member/login";

        String storeId = request.getParameter("storeId");
        String[] products = request.getParameterValues("productId");
        if (storeId == null || storeId.isBlank() || storeId.length() > 40
                || products == null || products.length == 0 || products.length > 100) {
            return "redirect:/cart?tab=pickup";
        }

        Set<String> remaining = new HashSet<>();
        for (String productId : products) {
            if (productId.isBlank() || productId.length() > 100) return "redirect:/cart?tab=pickup";
            remaining.add(productId);
        }

        List<CartBean> items = new ArrayList<>();
        long subtotal = 0;
        for (CartBean item : new CartDAO().getPickupCartList(memberId)) {
            if (!storeId.equals(item.getStoreId()) || !remaining.remove(item.getProductId())) continue;
            if (item.getQuantity() < 1 || item.getQuantity() > item.getStockQuantity()) {
                request.setAttribute("pickupOrderError", "매장 재고가 변경됐어요. 장바구니에서 수량을 확인해 주세요.");
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                return "order/pickup-checkout";
            }
            items.add(item);
            subtotal += item.getItemTotal();
        }
        if (!remaining.isEmpty() || items.isEmpty()) return "redirect:/cart?tab=pickup";

        request.setAttribute("pickupBuyer", new daiso.member.controller.MemberDAO().findProfileByMemberId(memberId));
        request.setAttribute("pickupOrderItems", items);
        request.setAttribute("pickupStoreName", items.get(0).getStoreName());
        request.setAttribute("pickupSubtotal", subtotal);
        request.setAttribute("pickupFee", 200);
        //매장픽업 주문서에서 사용할 토스페이 테스트 클라이언트 키 전달
        String tossClientKey = System.getenv("TOSS_PAYMENTS_CLIENT_KEY");
        String tossSecretKey = System.getenv("TOSS_PAYMENTS_SECRET_KEY");
        
        if(tossClientKey != null&& tossClientKey.startsWith("test_ck_")
        		&& tossSecretKey != null && tossSecretKey.startsWith("test_sk_")) {
        	request.setAttribute("tossClientKey", tossClientKey);
        }
        //매장 픽업 결제 준비 요청에 사용할 주문 검증 토큰
        synchronized(session) {
        	if(session.getAttribute("orderCsrf")==null) {
        		session.setAttribute("orderCsrf", java.util.UUID.randomUUID().toString());
        	}
        	request.setAttribute("orderCsrf", session.getAttribute("orderCsrf"));
        }
        		 
        response.setHeader("Cache-Control", "private, no-store");
        return "order/pickup-checkout";
    }


    @DaisoGetMapping("/order/form")
    public String orderForm(HttpServletRequest request) throws java.sql.SQLException {
        javax.servlet.http.HttpSession session = request.getSession(false);
        String memberId = session == null ? null : (String) session.getAttribute("member_id");
        if (memberId == null || memberId.isBlank()) return "redirect:/member/login";

        String[]selectedValues = request.getParameterValues("cartItemId");
        
        if (selectedValues ==null|| selectedValues.length ==0) {
        	return "redirect:/cart";
        }
        
        Set<Long> selectedIds = new HashSet<>();
        
        try {
        	for(String value : selectedValues) {
        		long id = Long.parseLong(value);
        		
        		if(id<=0) {
        			return "redirect:/cart";
        		}
        		
        		selectedIds.add(id);
        	}
        }catch (NumberFormatException e) {
        	return "redirect:/cart";
        }
        
        //로그인 한 회원의 장바구니만 조회
        List<CartBean> cartItems= new CartDAO().getCartList(memberId);
        
        //선택한 항목만 주문목록에 추가
        List<CartBean> items = new ArrayList<>();
        
        for(CartBean item : cartItems) {
        	if(selectedIds.contains(item.getCartItemId())) {
        		items.add(item);
        	}
        }
        //삭제된 항목이나 다른 회원번호가 섞이면 주문 진행 중단
        if(items.size() != selectedIds.size()) {
        	return "redirect:/cart";
        }
        long subtotal = 0;
        boolean purchasable = true;
        for (daiso.cart.db.CartBean item : items) {
            subtotal += item.getItemTotal();
            if (!item.isPurchasable()) purchasable = false;
        }
        long shipping = subtotal > 0 && subtotal < 30000 ? 3000L : 0L;
        if (session.getAttribute("orderCsrf") == null) {
            session.setAttribute("orderCsrf", java.util.UUID.randomUUID().toString());
        }
        request.setAttribute("orderItems", items);
        request.setAttribute("orderSubtotal", subtotal);
        long daisoPointBalance =new daiso.order.db.OrderDAO().getDaisoPointBalance(memberId);
        request.setAttribute("daisoPointBalance",daisoPointBalance);
        request.setAttribute("orderShipping", shipping);
        synchronized (session) {
            Object deliveryAddress = session.getAttribute("orderDeliveryAddress");

            if (!(deliveryAddress instanceof Map)) {
                try {
                    List<Map<String, String>> savedAddresses =
                            new DeliveryAddressDAO().findByMemberId(memberId);

                    if (!savedAddresses.isEmpty()) {
                        deliveryAddress = savedAddresses.get(0);
                        session.setAttribute("orderDeliveryAddress", deliveryAddress);
                    }
                } catch (java.sql.SQLException | javax.naming.NamingException e) {
                    request.getServletContext().log("주문서 배송지 조회 실패", e);
                }
            }

            request.setAttribute("deliveryAddress", deliveryAddress);
        }
        request.setAttribute("orderCsrf", session.getAttribute("orderCsrf"));
        request.setAttribute("deliveryMessage", session.getAttribute("orderDeliveryMessage"));
        if (!purchasable) request.setAttribute("orderError", "상품 상태 또는 재고를 확인해 주세요. 장바구니에서 수량을 변경하거나 상품을 삭제할 수 있습니다.");
        String tossClientKey = System.getenv("TOSS_PAYMENTS_CLIENT_KEY");
        String tossSecretKey = System.getenv("TOSS_PAYMENTS_SECRET_KEY");
        if(tossClientKey != null && tossClientKey.startsWith("test_ck")
        			&&tossSecretKey != null && tossSecretKey.startsWith("test_sk")) {
        	request.setAttribute("tossClientKey", tossClientKey);
        }
        request.setAttribute("pageName", "checkout");
        request.setAttribute("activeNavigation", "");
        return "shop/page";
    }
    
    @DaisoGetMapping("/order/delivery-summary")
    @daiso.common.annotation.DaisoResponseBody
    public String deliverySummary(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");

        javax.servlet.http.HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");

        JsonObject summary = new JsonObject();
        if (!(memberId instanceof String) || ((String) memberId).isBlank()) {
            response.setStatus(401);
            return summary.toString();
        }

        summary.addProperty("address", "");
        summary.addProperty("pickupStore", "");

        try {
            Object current = session.getAttribute("orderDeliveryAddress");

            if (!(current instanceof Map)) {
                List<Map<String, String>> addresses =
                        new DeliveryAddressDAO().findByMemberId((String) memberId);
                if (!addresses.isEmpty()) {
                    current = addresses.get(0);
                }
            }

            if (current instanceof Map) {
                Object address = ((Map<?, ?>) current).get("address1");
                if (address instanceof String) {
                    summary.addProperty("address", (String) address);
                }
            }

            Object pickupStoreId = session.getAttribute("orderPickupStoreId");
            if (pickupStoreId instanceof String) {
                for (Location store : PickupStores.findAll()) {
                    if (store.id.equals(pickupStoreId)) {
                        summary.addProperty("pickupStore", store.name);
                        break;
                    }
                }
            }

            return summary.toString();
        } catch (java.sql.SQLException | javax.naming.NamingException e) {
            request.getServletContext().log("헤더 배송지·픽업매장 조회 실패", e);
            response.setStatus(500);
            return "{}";
        }
    }
    
    @daiso.common.annotation.DaisoPostMapping("/order/address")
    @daiso.common.annotation.DaisoResponseBody
    public String saveDeliveryAddress(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {
        request.setCharacterEncoding("UTF-8");
        javax.servlet.http.HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("member_id") instanceof String)
                || ((String) session.getAttribute("member_id")).isBlank()) {
            return "로그인 후 이용해 주세요.";
        }
        Object csrf = session.getAttribute("orderCsrf");
        if (!(csrf instanceof String) || !csrf.equals(request.getParameter("csrfToken"))) {
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }
        String addressAction = request.getParameter("addressAction");
        String addressId = request.getParameter("addressId");

        if (addressAction != null
                && !"add".equals(addressAction)
                && !"edit".equals(addressAction)) {
            return "배송지 요청을 확인해 주세요.";
        }
        java.util.Map<String, String> address = new java.util.LinkedHashMap<>();
        String[] fields = {"addressName", "recipient", "phone", "postcode", "address1", "address2"};
        int[] limits = {50, 100, 30, 5, 255, 255};
        for (int i = 0; i < fields.length; i++) {
            String value = request.getParameter(fields[i]);
            value = value == null ? "" : value.strip();
            if ((!"address2".equals(fields[i]) && value.isEmpty()) || value.length() > limits[i]) {
                return "배송지의 필수 항목과 입력 길이를 확인해 주세요.";
            }
            address.put(fields[i], value);
        }
        if (!address.get("postcode").matches("[0-9]{5}")) return "우편번호는 5자리 숫자로 입력해 주세요.";
        String phone = address.get("phone").replaceAll("[\\s-]", "");
        if (!phone.matches("01[016789][0-9]{7,8}")) return "휴대폰번호를 확인해 주세요.";
        String memberId = (String) session.getAttribute("member_id");
        DeliveryAddressDAO addressDAO = new DeliveryAddressDAO();

        synchronized (session) {
            try {
                if ("edit".equals(addressAction)) {
                    if (addressId == null || addressId.isBlank()
                            || addressDAO.update(memberId, addressId, address) == 0) {
                        return "수정할 배송지를 찾을 수 없습니다.";
                    }
                } else {
                    addressId = java.util.UUID.randomUUID().toString();
                    addressDAO.insert(memberId, addressId, address);
                }
            } catch (java.sql.SQLException | javax.naming.NamingException e) {
                request.getServletContext().log("배송지 저장 실패", e);
                return "배송지를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }

            java.util.Map<String, java.util.Map<String, String>> addresses =
                    DeliveryAddressBook.entries(session);

            Object current = session.getAttribute("orderDeliveryAddress");
            Object currentId = current instanceof java.util.Map
                    ? ((java.util.Map<?, ?>) current).get("id")
                    : null;

            address.put("id", addressId);
            addresses.put(addressId, address);

            // 주문서·장바구니에서 등록한 주소는 이번 주문에 바로 적용한다.
            if (addressAction == null || currentId == null || addressId.equals(currentId)) {
                session.setAttribute("orderDeliveryAddress", address);
            }
        }
        return "OK";
    }
    
    @daiso.common.annotation.DaisoPostMapping("/order/pickup-store")
    @daiso.common.annotation.DaisoResponseBody
    public String savePickupStore(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {

        request.setCharacterEncoding("UTF-8");

        javax.servlet.http.HttpSession session =
                request.getSession(false);

        if (session == null
                || !(session.getAttribute("member_id") instanceof String)
                || ((String) session.getAttribute("member_id")).isBlank()) {
            return "로그인 후 이용해 주세요.";
        }

        Object csrf = session.getAttribute("orderCsrf");
        if (!(csrf instanceof String)
                || !csrf.equals(request.getParameter("csrfToken"))) {
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }

        String storeId = request.getParameter("storeId");

        try {
            for (Location store : PickupStores.findAll()) {
                if (store.id.equals(storeId)) {
                    session.setAttribute("orderPickupStoreId", store.id);
                    return "OK";
                }
            }
            return "목록에 있는 픽업매장을 선택해 주세요.";
        } catch (java.sql.SQLException | javax.naming.NamingException e) {
            request.getServletContext().log("픽업매장 선택 처리 실패", e);
            return "픽업매장 목록을 불러오지 못했습니다.";
        }
    }

    @daiso.common.annotation.DaisoPostMapping("/order/delivery-message")
    @daiso.common.annotation.DaisoResponseBody
    public String saveDeliveryMessage(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {
        request.setCharacterEncoding("UTF-8");
        javax.servlet.http.HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("member_id") instanceof String)
                || ((String) session.getAttribute("member_id")).isBlank()) return "로그인 후 이용해 주세요.";
        Object csrf = session.getAttribute("orderCsrf");
        if (!(csrf instanceof String) || !csrf.equals(request.getParameter("csrfToken"))) {
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }
        String message = request.getParameter("deliveryMessage");
        message = message == null ? "" : message.strip();
        if (message.length() > 500) return "배송 요청사항은 500자 이내로 입력해 주세요.";
        session.setAttribute("orderDeliveryMessage", message);
        return "OK";
    }


		//주문 생성
		public String orderCreate(HttpServletRequest request) {
			//배송정보 확인 후 주문 저장
			return "redirect:/order/list";
		}
		//내 주문 목록
		@DaisoGetMapping("/order/list")
		public String orderList(HttpServletRequest request) {
			//로그인 회원의 주문 목록 조회
			request.setAttribute("pageName", "orders");
			request.setAttribute("activeNavigation", "");
			return "shop/page";
		}
		//내 주문 상세
		@DaisoGetMapping("/order/detail")
		public String orderDetail(HttpServletRequest request) {
			//주문 번호와 소유자 확인 후 상세 조회
			request.setAttribute("pageName","order");
			request.setAttribute("activeNavigation","");
			return "shop/page";
		}
		//주문 취소
		public String orderCancle(HttpServletRequest request){
			//취소 가능 상태 확인 후 취소 및 재고 복구
			return "redirect:/order/list";
		}
		@DaisoGetMapping("/order/success")
		public String orderSuccess(HttpServletRequest request) {
			request.setAttribute("pageName", "payment-success");
			request.setAttribute("activeNavigation", "");
			
			return "shop/page";
		}
		@DaisoGetMapping("/order/failure")
		public String orderFailure(HttpServletRequest request) {
			request.setAttribute("pageName","payment-failure" );
			request.setAttribute("activeNavigation", "");
			
			return "shop/page";
		
		}
}
