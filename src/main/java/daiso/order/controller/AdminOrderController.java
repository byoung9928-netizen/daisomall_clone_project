package daiso.order.controller;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.order.db.AdminOrderDAO;
import daiso.order.db.OrderBean;

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

        try {
            AdminOrderDAO dao = new AdminOrderDAO();

            List<OrderBean> orders =
                dao.getOrderList(keyword);

            request.setAttribute("adminOrders", orders);

        } catch (SQLException e) {
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
}