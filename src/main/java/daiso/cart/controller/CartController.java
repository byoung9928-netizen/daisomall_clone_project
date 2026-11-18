package daiso.cart.controller;
import daiso.common.annotation.*;
import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.ArrayList;
import daiso.cart.db.CartBean;
import daiso.cart.db.CartDAO;

@DaisoController //이 클래스가 Controller임
public class CartController {
	
	@DaisoGetMapping("/cart") //장바구니 조회 주소 연결
	
	public String cartList(HttpServletRequest request){
		
		  HttpSession session = request.getSession(false);

		    String memberId = session == null
		        ? null
		        : (String) session.getAttribute("member_id");

		    if (memberId == null || memberId.isBlank()) {
		        return "redirect:/member/login";
		    }

		    CartDAO dao = new CartDAO();

		    try {
		        List<CartBean> cartList = dao.getCartList(memberId);
		        request.setAttribute("cartList", cartList);

		    } catch (SQLException e) {
		        throw new IllegalStateException(
		            "장바구니 조회에 실패했습니다.", e
		        );
		    }
		    if(session.getAttribute("orderCsrf")== null) {
		    	session.setAttribute("orderCsrf", java.util.UUID.randomUUID().toString());
		    }
		    
		    request.setAttribute("orderCsrf", session.getAttribute("orderCsrf"));
		    request.setAttribute("deliveryAddress", session.getAttribute("orderDeliveryAddress"));
		    request.setAttribute("pageName", "cart");
		    request.setAttribute("activeNavigation", "");

		
		return "shop/page";
		
	}
	@DaisoPostMapping("/cart/add")
	@DaisoResponseBody
	public String cartAdd(HttpServletRequest request) {
		//로그인한 회원 확인
	    HttpSession session = request.getSession(false);

	    String memberId = session == null
	        ? null : (String) session.getAttribute("member_id");
	    //회원번호가 없으면 저장없이 안내 반환
	    if (memberId == null || memberId.isBlank()) {
	        return "로그인 후 이용해 주세요.";
	    }
	    //폼에서 name="productId"로 보낸 상품 번호를 문자열로 꺼냄
	    String productId = request.getParameter("productId");
	    //상품 번호가 없거나 공백이면 저장하지않고 안내를 반환
	    if (productId == null || productId.isBlank()) {
	        return "상품 번호가 필요합니다.";
	    }

	    try {
	    	//name-"quantity"로 받은 문자열을 정수로 변환
	        int quantity =
	            Integer.parseInt(request.getParameter("quantity"));
	        //db작업을 담당하는 db객체 생성
	        CartDAO dao = new CartDAO();
	        //로그인 회원번호 상품번호 수량을 전달해 db저장 요청
	        //판매상태 재고 수량제한은 dao에서 확인
	        dao.addCart(memberId, productId, quantity);
	        
	        
	        return "OK";

	    } catch (NumberFormatException e) {
	        return "수량은 정수로 입력해 주세요.";

	    } catch (IllegalArgumentException e) {
	    	//dao에서 수량 재고 판매 상태등 장바구니에 담을수 없는 문제가 발생했을 때
	        return e.getMessage();

	    } catch (SQLException e) {
	    	//db작업 중 오류가 발생했을 때 서버 콘솔에 원인 출력
	        e.printStackTrace();
	        return "장바구니 저장에 실패했습니다.";
	    }
	}
		@DaisoPostMapping("/cart/update")
		@DaisoResponseBody
		public String cartUpdate(HttpServletRequest request) {
			HttpSession session = request.getSession(false);
			
			String memberId = session ==null
					? null : (String) session.getAttribute("member_id");
			if(memberId == null || memberId.isBlank()) {
				return	"로그인 후 이용해 주세요.";
			}
			try {
				long cartItemId =
						Long.parseLong(request.getParameter("cartItemId"));
				int change = 
					Integer.parseInt(request.getParameter("change"));
				
				CartDAO dao = new CartDAO();
				
				boolean updated = dao.updateCartQuantity(memberId, cartItemId, change);
				
				if (!updated) {
					return "수량을 변경할 수 없습니다."
							+"상품 상태,재고,수량을 확인해 주세요.";
				}
				return "OK";
				
			}catch (NumberFormatException e) {
				return "장바구니 번호와 변경 수량을 확인해 주세요.";
				
			}catch (IllegalArgumentException e) {
				return e.getMessage();
			}catch (SQLException e) {
				e.printStackTrace();
				return "수량 변경 중 오류가 발생했습니다.";
			}
			
			
	}

		@DaisoPostMapping("/cart/delete")
		@DaisoResponseBody
		public String cartDelete(HttpServletRequest request) {
			HttpSession session = request.getSession(false);
			
			String memberId = session == null
					? null : (String) session.getAttribute("member_id");
			
			if (memberId == null || memberId.isBlank()) {
				return "로그인 후 이용해 주세요.";
			}
			
			try {
				long cartItemId = Long.parseLong(request.getParameter("cartItemId"));
				
				CartDAO dao = new CartDAO();
				boolean deleted = dao.deleteCart(memberId, cartItemId);
				
				if (!deleted) {
					return "삭제할 장바구니 상품을 찾을 수 없습니다.";
				}
				
				return "OK";
						
			}catch (NumberFormatException e) {
				return "장바구니 번호를 확인해 주세요.";
			}catch (SQLException e) {
				e.printStackTrace();
				return "장바구니 삭제중 오류가 발생했습니다";
			}
			
	}
		@DaisoPostMapping("/cart/delete-selected")
		@DaisoResponseBody
		public String cartDeleteSeleted(HttpServletRequest request) {
			HttpSession session = request.getSession(false);
			String memberId = session ==null
					? null : (String) session.getAttribute("member_id");
			if (memberId == null || memberId.isBlank()) {
				return "로그인 후 이용해 주세요.";
			}
			String[] values = request.getParameterValues("cartItemId");
			
			if(values == null || values.length ==0) {
				return "삭제할 상품을 선택해 주세요.";
			}
			try {
				long[] cartItemIds =new long[values.length];
				for(int i =0; i<values.length; i++) {
					cartItemIds[i] = Long.parseLong(values[i]);
					
					if (cartItemIds[i]<=0) {
						return "장바구니 번호를 확인해 주세요.";
					}
				}
				CartDAO dao= new CartDAO();
				int deletedCount = dao.deleteSelectedCart(memberId,cartItemIds);
				
				if(deletedCount ==0) {
					return "삭제할 상품을 찾을 수 없습니다.";
				}
				return "OK";
				
			}catch(NumberFormatException e) {
				return "장바구니 번호를 확인해 주세요.";
				
			}catch(IllegalArgumentException e) {
				return e.getMessage();
			}catch (SQLException e) {
				e.printStackTrace();
				return "선택한 상품 삭제 중 오류가 발생했습니다.";
			}
		}
}

