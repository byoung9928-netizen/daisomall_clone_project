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

		    request.setAttribute("pageName", "cart");
		    request.setAttribute("activeNavigation", "");

		
		return "shop/page";
		
	}
	@DaisoPostMapping("/cart/add")
	public String cartAdd(HttpServletRequest request) {
		//상품 번호와 수량을 받아 DAO 호출
		String productId=request.getParameter("productId");
		String quantityValue = request.getParameter("quantity");
		if(productId==null||productId.trim().isEmpty()) {
			throw new IllegalArgumentException("상품 번호가 필요 합니다.");
		}
		int quantity;
		try {
			quantity=Integer.parseInt(quantityValue);
			
		}catch (NumberFormatException e) {
			throw new IllegalArgumentException("수량은 정수로 입력해야합니다.");
		}
		
		if (quantity<1||quantity>10) {
			throw new IllegalArgumentException("수량은 1~99개여야 합니다.");
		}
		//나중에 로그인 회원 확인 및 DAO 호출
		return "redirect:/cart";
	}
	public String cartUpdate(HttpServletRequest request) {
		//나중에 장바구니 항목 번호와 변경할 수량을 받아 DAO호출
		return "redirect:/cart";
	}
	
	public String cartDelete(HttpServletRequest request) {
		//나중에 장바구니 항목번호를 받아 DAO 호출
		return "redirect:/cart";
	}
}
