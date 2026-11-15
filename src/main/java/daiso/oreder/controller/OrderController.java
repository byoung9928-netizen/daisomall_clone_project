package daiso.oreder.controller;

import javax.servlet.http.HttpServletRequest;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;

@DaisoController
public class OrderController {
		//주문서 화면
		@DaisoGetMapping("/order/form")
		public String orderForm(HttpServletRequest request) {
			request.setAttribute("pageName","checkout");
			request.setAttribute("activeNavigation","");
			return "shop/page";
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
