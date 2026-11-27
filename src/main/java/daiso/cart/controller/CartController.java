package daiso.cart.controller;

import daiso.product.service.ProductBehaviorRecorder;
import daiso.product.service.ProductBehaviorRecorder.Type;
import daiso.common.annotation.*;
import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.ArrayList;
import daiso.cart.db.CartBean;
import daiso.cart.db.CartDAO;
import java.util.Map;
import daiso.order.db.DeliveryAddressDAO;

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
		        List<CartBean> pickupCartList = dao.getPickupCartList(memberId);
		        request.setAttribute("pickupCartList", pickupCartList);
                request.setAttribute("pickupTab", CartTab.openPickup(request, memberId,
                        cartList.isEmpty() && !pickupCartList.isEmpty()));

		    } catch (SQLException e) {
		        throw new IllegalStateException(
		            "장바구니 조회에 실패했습니다.", e
		        );
		    }
		    if(session.getAttribute("orderCsrf")== null) {
		    	session.setAttribute("orderCsrf", java.util.UUID.randomUUID().toString());
		    }
		    
		    request.setAttribute("orderCsrf", session.getAttribute("orderCsrf"));
		    Object deliveryAddress = session.getAttribute("orderDeliveryAddress");

		    if (!(deliveryAddress instanceof Map)) {
		        try {
		            List<Map<String, String>> savedAddresses =
		                    new DeliveryAddressDAO().findByMemberId(memberId);

		            if (!savedAddresses.isEmpty()) {
		                deliveryAddress = savedAddresses.get(0);
		            }
		        } catch (SQLException | javax.naming.NamingException e) {
		            request.getServletContext().log("장바구니 배송지 조회 실패", e);
		        }
		    }

		    request.setAttribute("deliveryAddress", deliveryAddress);		    request.setAttribute("pageName", "cart");
		    request.setAttribute("activeNavigation", "");

		
		return "shop/page";
		
	}
	//1. 시작
	@DaisoPostMapping("/cart/add")
	@DaisoResponseBody
	public String cartAdd(HttpServletRequest request, HttpServletResponse response) {
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
	        if (dao.addCartAndCheckNew(memberId, productId, quantity)) {
	            ProductBehaviorRecorder.record(request, Type.CART_ADD, productId);
	        }
            CartTab.remember(request, response, memberId, false);
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
	
	//매장픽업
	@DaisoPostMapping("/cart/pickup/add")
	@DaisoResponseBody
	public String pickupCartAdd(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		String memberId = session ==null
				? null :(String)session.getAttribute("member_id");
		if(memberId == null || memberId.isBlank()) {
			return "로그인 후 이용해 주세요.";
		}
		String storeId = request.getParameter("storeId");
		String productId = request.getParameter("productId");
		
		if(storeId == null || storeId.isBlank() || storeId.length()>40) {
			return"픽업 매장을 선택해 주세요.";
		}
		if(productId ==null|| productId.isBlank() || productId.length()>100) {
			return "상품 번호를 확인해 주세요.";
		}
		
		try {
			int quantity=Integer.parseInt(request.getParameter("quantity"));
			
			//DAO에서 선택한 매장의 재고와 이미 담은 수량을 확인한다.
			CartDAO dao = new CartDAO();
			if (dao.addPickupCart(memberId, storeId, productId, quantity)) {
				ProductBehaviorRecorder.record(request, Type.CART_ADD, productId);
			}
            CartTab.remember(request, response, memberId, true);
            return "OK";
		}catch (NumberFormatException e) {
			return"수량은 정수로 입력해 주세요.";
		}catch (IllegalArgumentException e) {
			return e.getMessage();
		}catch(SQLException e) {
			request.getServletContext().log("매장픽업 장바구니 저장 실패", e);
			return "매장 픽업 장바구니에 담지 못했습니다.";
		}
		
	}
	//로그인한 회원의 픽업 장바구니에서 상품 한 건 삭제
	@DaisoPostMapping("/cart/pickup/delete")
	@DaisoResponseBody
	public String pickupCartDelete(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		String memberId = session ==null
				? null : (String) session.getAttribute("member_id");
		
		 if (memberId == null || memberId.isBlank()) {
		        return "로그인 후 이용해 주세요.";
		    }

		    String storeId = request.getParameter("storeId");
		    String productId = request.getParameter("productId");

		    if (storeId == null || storeId.isBlank() || storeId.length() > 40
		            || productId == null || productId.isBlank() || productId.length() > 100) {
		        return "삭제할 매장과 상품을 확인해 주세요.";
		    }

		    try {
		        boolean deleted = new CartDAO().deletePickupCart(memberId, storeId, productId);
		        if (deleted) ProductBehaviorRecorder.record(request, Type.CART_REMOVE, productId);
		        return deleted ? "OK" : "삭제할 픽업 상품을 찾을 수 없습니다.";
		    } catch (SQLException e) {
		        request.getServletContext().log("픽업 장바구니 삭제 실패", e);
		        return "픽업 상품 삭제 중 오류가 발생했습니다.";
		    }
		}
	@DaisoPostMapping("/cart/pickup/update")
	@DaisoResponseBody
	public String pickupCartUpdate(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		String memberId = session ==null
				? null :(String) session.getAttribute("member_id");
		
		if(memberId == null || memberId.isBlank()) {
			return"로그인 후 이용해 주세요.";
		}
		String storeId=request.getParameter("storeId");
		String productId=request.getParameter("productId");
		
		if(storeId == null||storeId.isBlank()||storeId.length()>40
				||productId == null || productId.isBlank()|| productId.length()>100) {
			return "픽업 매장과 상품을 확인해 주세요.";
							}
		try {
			int change=Integer.parseInt(request.getParameter("change"));
			boolean updated = new CartDAO()
					.updatePickupQuantity(memberId, storeId, productId, change);
			return updated ? "OK":"상품수량과 매장 재고를 확인해 주세요.";
			
		}catch(NumberFormatException e) {
			return "변경할 수량을 확인해 주세요.";
		}catch(IllegalArgumentException e) {
			return e.getMessage();
		}catch(SQLException e) {
			request.getServletContext().log("픽업 장바구니 수량 변경 실패",e);
			return "픽업상품 수량을 변경하지 못했습니다.";
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
				List<String> removed = dao.deleteCartItems(memberId, new long[] { cartItemId });
				if (removed.isEmpty()) {
					return "삭제할 장바구니 상품을 찾을 수 없습니다.";
				}
				
				ProductBehaviorRecorder.record(request, Type.CART_REMOVE, removed);
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
				List<String> removed = dao.deleteCartItems(memberId, cartItemIds);
				if (removed.isEmpty()) {
					return "삭제할 상품을 찾을 수 없습니다.";
				}
				ProductBehaviorRecorder.record(request, Type.CART_REMOVE, removed);
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

