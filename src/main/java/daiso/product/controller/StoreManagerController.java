package daiso.product.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.member.controller.MemberService;
import daiso.order.db.OrderDAO;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.HistoryFilter;
import daiso.product.dao.StoreInventoryDAO.InventoryFilter;
import daiso.product.dao.StoreInventoryDAO.Sort;
import daiso.product.dao.StoreInventoryDAO.State;
import daiso.product.dao.StoreInventoryDAO.Stock;
import daiso.common.annotation.DaisoResponseBody;

@DaisoController
public class StoreManagerController {
    private static final int PAGE_SIZE = 20;
    
    private static final String ASSIGNMENT_SQL =
            "SELECT s.store_id, s.store_name "
            + "FROM store_manager_assignments a "
            + "JOIN members m ON m.member_id = a.member_id "
            + "JOIN store_locations s ON s.store_id = a.store_id "
            + "WHERE a.member_id = ? "
            + "AND m.role = 'STORE_MANAGER' AND m.status = 'ACTIVE'";


    @DaisoGetMapping("/store-manager/inventory")
    public String inventory(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");

        if (!(memberId instanceof String)) {
            return "redirect:/member/login";
        }

        

        try (Connection connection = ProductQueries.connect()) {
            String storeId;
            String storeName;

            try (PreparedStatement statement = connection.prepareStatement(ASSIGNMENT_SQL)) {
                statement.setString(1, (String) memberId);

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        response.sendError(403, "배정된 매장이 있는 관리자만 이용할 수 있습니다.");
                        return null;
                    }

                    storeId = result.getString("store_id");
                    storeName = result.getString("store_name");
                }
            }

            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            boolean showHistory = "history".equals(request.getParameter("mode"));
            InventoryFilter inventoryFilter =
                    new InventoryFilter(storeId, "", "", "", State.ALL);
            HistoryFilter historyFilter =
                    new HistoryFilter(null, null, "", "", "", false);

            long total = showHistory
                    ? dao.countHistory(connection, storeId, "", historyFilter)
                    : dao.countInventory(connection, inventoryFilter);
            long pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            int requestedPage = ProductQueries.integer(
                    request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
            int page = (int) Math.min(requestedPage, pages);

            request.setAttribute("storeManagerName", storeName);
            request.setAttribute("storeManagerId", storeId);
            request.setAttribute("storeManagerCsrf", AdminProductController.token(session));
            request.setAttribute("storeManagerShowHistory", showHistory);

            int offset = (page - 1) * PAGE_SIZE;
            if (showHistory) {
                request.setAttribute("storeManagerHistory",
                        dao.history(connection, storeId, "", historyFilter, PAGE_SIZE, offset));
            } else {
                request.setAttribute("storeManagerStock",
                        dao.inventory(connection, inventoryFilter, Sort.NAME, PAGE_SIZE, offset));
            }
            request.setAttribute("storeManagerPage", page);
            request.setAttribute("storeManagerPages", pages);
            
           
            connection.commit();
            return "store-manager/inventory";
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 관리자 재고 조회 실패", error);
            response.sendError(503, "매장 재고를 불러오지 못했습니다.");
            return null;
        }
    }
    
    @DaisoGetMapping("/store-manager/pickup-orders")
    public String pickupOrders(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");

        if (!(memberId instanceof String)) {
            return "redirect:/member/login";
        }

        try (Connection connection = ProductQueries.connect()) {
            String storeId;
            String storeName;

            try (PreparedStatement statement = connection.prepareStatement(ASSIGNMENT_SQL)) {
                statement.setString(1, (String) memberId);

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        response.sendError(403, "배정된 매장이 있는 관리자만 이용할 수 있습니다.");
                        return null;
                    }

                    storeId = result.getString("store_id");
                    storeName = result.getString("store_name");
                }
            }

            request.setAttribute("storeManagerName", storeName);
            request.setAttribute("storeManagerCsrf", AdminProductController.token(session));
            request.setAttribute("pickupOrders",
                    new OrderDAO().findPaidPickupOrders(connection, storeId));

            connection.commit();
            return "store-manager/pickup-orders";
        } catch (SQLException | NamingException | IllegalStateException error) {
            request.getServletContext().log("매장 픽업 주문 조회 실패", error);
            response.sendError(503, "픽업 주문을 불러오지 못했습니다.");
            return null;
        }
    }
    
 // 스캔한 QR이 로그인한 관리자의 담당 매장 주문인지 조회한다.
    @DaisoPostMapping("/store-manager/pickup-orders/scan")
    @DaisoResponseBody
    public String scanPickupOrder(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");

        if (!(memberId instanceof String)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return "매장 관리자 계정으로 로그인해 주세요.";
        }

        if (!AdminProductController.validToken(request, session)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "픽업 주문 화면을 다시 열어 주세요.";
        }

        String pickupToken = request.getParameter("pickupToken");
        if (pickupToken == null || !pickupToken.matches("[A-Za-z0-9_-]{43}")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "픽업 QR을 다시 스캔해 주세요.";
        }

        try (Connection connection = ProductQueries.connect();
                PreparedStatement assignment = connection.prepareStatement(ASSIGNMENT_SQL)) {
            assignment.setString(1, (String) memberId);

            String storeId;
            try (ResultSet result = assignment.executeQuery()) {
                if (!result.next()) {
                    connection.rollback();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return "배정된 매장이 있는 관리자만 이용할 수 있습니다.";
                }
                storeId = result.getString("store_id");
            }

            String orderNo = new OrderDAO().findReadyPickupOrderNoByToken(
                    connection, storeId, pickupToken);
            connection.commit();

            if (orderNo == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "이 QR로 수령할 수 있는 주문을 찾지 못했습니다.";
            }

            return orderNo;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("픽업 QR 주문 조회 실패", error);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "픽업 주문을 확인하지 못했습니다.";
        }
    }
    @DaisoPostMapping("/store-manager/pickup-orders/status")
    public String updatePickupStatus(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");
        if (!(memberId instanceof String)) {
            return "redirect:/member/login";
        }

        if (!AdminProductController.validToken(request, session)) {
            response.sendError(403, "픽업 주문 화면을 다시 열어 주세요.");
            return null;
        }

        String orderNo = request.getParameter("orderNo");
        String action = request.getParameter("action");
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > 100
                || !("ready".equals(action) || "complete".equals(action))) {
            response.sendError(400, "주문번호와 처리 동작을 확인해 주세요.");
            return null;
        }

        try (Connection connection = ProductQueries.connect()) {
            String storeId;
            try (PreparedStatement statement =
                         connection.prepareStatement(ASSIGNMENT_SQL + " FOR UPDATE")) {
                statement.setString(1, (String) memberId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        response.sendError(403, "배정된 매장이 있는 관리자만 이용할 수 있습니다.");
                        return null;
                    }
                    storeId = result.getString("store_id");
                }
            }

            boolean completing = "complete".equals(action);
            String pickupToken =null;
            if(completing) {
            	pickupToken=request.getParameter("pickupToken");
            	if(pickupToken ==null||!pickupToken.matches("[A-Za-z0-9__-]{43}")) {
            		connection.rollback();
            		response.sendError(400,"픽업QR을 다시 스캔해 주세요.");
            		return null;
            	}
            	boolean passwordVerified;
            	try {
            		passwordVerified=new MemberService().verifyStoreManagerPassword((String)memberId, request.getParameter("approvalPassword"));
            	}catch(Exception error) {
            		connection.rollback();
            		request.getServletContext().log("픽업 승인정보 확인 실패", error);
            		response.sendError(503,"승인 정보를 확인하지 못했습니다.");
            		return null;
            	}
            	if(!passwordVerified) {
            		connection.rollback();
            		response.sendError(403,"매장 관리자비밀번호가 올바르지 않습니다.");
            		return null;
            	}
            }
            boolean changed=new OrderDAO().advancePickupStatus(
            		connection, storeId,orderNo,completing,pickupToken);
            if (!changed) {
                connection.rollback();
                response.sendError(409, "처리할 수 없는 주문입니다. 목록을 새로고침해 주세요.");
                return null;
            }

            connection.commit();
            return "redirect:/store-manager/pickup-orders";
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("픽업 주문 상태 변경 실패", error);
            response.sendError(503, "픽업 주문 상태를 변경하지 못했습니다.");
            return null;
        }
    }
    
    @DaisoPostMapping("/store-manager/inventory/adjust")
    public String adjust(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");
        if (!(memberId instanceof String)) {
            return "redirect:/member/login";
        }

        if (!AdminProductController.validToken(request, session)) {
            response.sendError(403, "재고 화면을 다시 연 뒤 시도해 주세요.");
            return null;
        }

        try {
            String productId = ProductQueries.text(request, "productId", 100);
            String viewedStoreId = ProductQueries.text(request, "viewStoreId", 40);
            String reason = ProductQueries.text(request, "reason", 255);
            Change change = Change.valueOf(ProductQueries.text(request, "change", 12));
            Integer amount = ProductQueries.integer(request, "amount", null, 0, Integer.MAX_VALUE);
            long version = Long.parseLong(ProductQueries.text(request, "version", 19));
            int page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);

            if (productId.isEmpty() || reason.isEmpty() || amount == null || version < 0
                    || !java.util.Set.of(Change.RECEIVE, Change.DAMAGE, Change.COUNT).contains(change)) {
                response.sendError(400, "변경 유형, 수량, 사유를 확인해 주세요.");
                return null;
            }

            try (Connection connection = ProductQueries.connect()) {
                try {
                    String storeId;

                    try (PreparedStatement statement =
                                 connection.prepareStatement(ASSIGNMENT_SQL + " FOR UPDATE")) {
                        statement.setString(1, (String) memberId);

                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                response.sendError(403, "배정된 매장이 있는 관리자만 이용할 수 있습니다.");
                                return null;
                            }
                            storeId = result.getString("store_id");
                        }
                    }

                    if (!storeId.equals(viewedStoreId)) {
                        connection.rollback();
                        response.sendError(409, "담당 매장이 변경되었습니다. 재고 화면을 다시 열어 주세요.");
                        return null;
                    }

                    StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
                    Stock before = dao.find(connection, storeId, productId, true);

                    if (before == null || !before.carried || before.version != version) {
                        connection.rollback();
                        response.sendError(409, "재고가 변경됐거나 취급이 종료됐습니다. 목록을 새로고침해 주세요.");
                        return null;
                    }

                    int after;
                    try {
                        after = change.after(before.quantity, amount);
                    } catch (IllegalArgumentException error) {
                        connection.rollback();
                        response.sendError(400, error.getMessage());
                        return null;
                    }

                    dao.update(connection, storeId, before, after, change, reason, (String) memberId);
                    connection.commit();
                    return "redirect:/store-manager/inventory?page=" + page;
                } catch (SQLException error) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        error.addSuppressed(rollbackError);
                    }
                    throw error;
                }
            }
        } catch (IllegalArgumentException error) {
            response.sendError(400, "변경 유형, 수량, 사유를 확인해 주세요.");
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 관리자 재고 조정 실패", error);
            response.sendError(503, "재고를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return null;
        }
    }
    
}