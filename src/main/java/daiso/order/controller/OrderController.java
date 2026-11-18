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


@DaisoController
public class OrderController {
    // 기존 orderForm()을 이 메서드로 교체합니다.
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
        request.setAttribute("orderShipping", shipping);
        request.setAttribute("deliveryAddress", session.getAttribute("orderDeliveryAddress"));
        request.setAttribute("orderCsrf", session.getAttribute("orderCsrf"));
        request.setAttribute("deliveryMessage", session.getAttribute("orderDeliveryMessage"));
        if (!purchasable) request.setAttribute("orderError", "상품 상태 또는 재고를 확인해 주세요. 장바구니에서 수량을 변경하거나 상품을 삭제할 수 있습니다.");
        request.setAttribute("pageName", "checkout");
        request.setAttribute("activeNavigation", "");
        return "shop/page";
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
        session.setAttribute("orderDeliveryAddress", address);
        return "OK";
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
