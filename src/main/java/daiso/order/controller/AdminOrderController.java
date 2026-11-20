package daiso.order.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.JsonObject;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.order.db.AdminOrderDAO;
import daiso.order.db.OrderBean;
import daiso.order.delivery.DeliveryTrackerClient;

@DaisoController
public class AdminOrderController {

    @DaisoGetMapping("/admin/orders")
    public String orderList(
            HttpServletRequest request,
            HttpServletResponse response) {

    	

        request.setAttribute("adminSection", "orders");
        request.setAttribute(
            "adminOrders", Collections.emptyList()
        );
        request.setAttribute("adminOrderQuery", "");
        //query 검색어 받기, 없으면 빈 문자열, trim은 앞뒤 공백 제거
        String keyword = request.getParameter("query");
        keyword = keyword == null ? "" : keyword.trim();

        if (keyword.length() > 120) {
            response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST
            );

            request.setAttribute(
                "adminOrderError",
                "검색어는 120자 이내로 입력해 주세요."
            );

            return "admin/orders";
        }

        request.setAttribute("adminOrderQuery", keyword);

        int page = 1;
        String pageValue = request.getParameter("page");

        try {
            if (pageValue != null && !pageValue.isBlank()) {
                page = Integer.parseInt(pageValue);
            }

            if (page < 1) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            request.setAttribute(
                "adminOrderError",
                "페이지 번호를 확인해 주세요."
            );

            return "admin/orders";
        }

        try {
            AdminOrderDAO dao = new AdminOrderDAO();

            List<OrderBean> orders =
                dao.getOrderList(keyword, page);

            boolean hasNext = orders.size() > 50;

            // 다음 페이지 확인용으로 가져온 51번째 항목 제거
            if (hasNext) {
                orders.remove(50);
            }

            request.setAttribute("adminOrders", orders);
            request.setAttribute("adminOrderPage", page);
            request.setAttribute("adminOrderHasNext", hasNext); 
        }catch (SQLException e) {
            request.getServletContext().log(
                "관리자 주문 목록 조회 실패", e
            );

            response.setStatus(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            request.setAttribute(
                "adminOrderError",
                "주문 목록을 불러오지 못했습니다."
            );
        }

        return "admin/orders";
    }
    @DaisoGetMapping("/admin/orders/detail")
    public String orderDetail(
    		HttpServletRequest request,
    		HttpServletResponse response) {
    	
    	request.setAttribute("adminSection","orders");
    	
    	String orderNo=request.getParameter("orderNo");
    	
    	if(orderNo == null || orderNo.isBlank()||orderNo.length()>100) {
    		response.setStatus(
    				HttpServletResponse.SC_BAD_REQUEST
    				);
    		request.setAttribute("adminOrderError", "주문번호를 확인해 주세요");
    		
    		return "admin/order-detail";
    	}
    	try {
    		AdminOrderDAO dao = new AdminOrderDAO();
    		
    		OrderBean order = dao.getOrderDetail(orderNo);
    		
    		if(order == null) {
    			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    		}else {
    			request.setAttribute("adminOrder", order);
    			HttpSession session = request.getSession();
    			if(session.getAttribute("adminOrderCsrf")==null) {
    				session.setAttribute("adminOrderCsrf", UUID.randomUUID().toString());
    			}
    			request.setAttribute("adminOrderCsrf", session.getAttribute("adminOrderCsrf"));
    		}
    	}catch(SQLException e) {
    		request.getServletContext().log("관리자 주문 상세 조회 실패",e);
    		
    		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    		request.setAttribute("adminOrderError", "주문 상세정보를 불러오지 못했습니다.");
    	}
    	return "admin/order-detail";
    }
    @DaisoPostMapping("/admin/orders/tracking")
    @DaisoResponseBody
    public String tracking(HttpServletRequest request) {
    	JsonObject result = new JsonObject();
    	result.addProperty("success", false);
    	
    	HttpSession session=request.getSession(false);
    	Object savedToken = session ==null
    			? null : session.getAttribute("adminOrderCsrf");
    	String csrfToken = request.getParameter("csrfToken");
    	
    	if(!(savedToken instanceof String)||!savedToken.equals(csrfToken)) {
    		result.addProperty("message", "주문 상세 화면을 새로 열고 다시 시도해 주세요.");
    		
    		return result.toString();
    	}
    	String orderNo=request.getParameter("orderNo");
    	
    	
    	if (orderNo == null || orderNo.isBlank() || orderNo.length() > 100) {
    		
    		result.addProperty("message", "주문번호를 확인해 주세요.");
    		return result.toString();
    	}
    	
    	try {
    		AdminOrderDAO dao = new AdminOrderDAO();
    		OrderBean order =dao.getOrderDetail(orderNo);
    		
    		if(order ==null) {
    			result.addProperty("message", "주문을 찾을 수 없습니다.");
    			return result.toString();
    		}
    	String carrierId = order.getCarrierId();
    	String trackingNumber = order.getTrackingNumber();
    	if(carrierId == null || carrierId.isBlank()||trackingNumber ==null||trackingNumber.isBlank()) {
    		result.addProperty("message", "이 주문에 운송장을 먼저 등록해 주세요.");
    		return result.toString();
    	}
    	DeliveryTrackerClient client = new DeliveryTrackerClient();
    	
    	JsonObject tracking = client.track(carrierId,trackingNumber);
    	result.addProperty("success", true);
        result.add("tracking", tracking);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();

        result.addProperty(
            "message",
            "배송조회 요청이 중단되었습니다."
        );

    } catch (IOException e) {
        result.addProperty("message", e.getMessage());

    } catch (IllegalArgumentException e) {
        result.addProperty("message", e.getMessage());

    } catch (SQLException e) {
        request.getServletContext().log(
            "배송조회 대상 주문 조회 실패", e
        );

        result.addProperty(
            "message",
            "주문정보를 불러오지 못했습니다."
        );

    } catch (RuntimeException e) {
        result.addProperty(
            "message",
            "배송조회 설정 또는 응답을 확인해 주세요."
        );
    }

    return result.toString();
    }
    	
    	
}
    
