package daiso.product.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.member.controller.MemberService;
import daiso.order.db.OrderDAO;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.HistoryFilter;

@DaisoController
public class StoreManagerController {
    private static final int PAGE_SIZE = 20;

    private static final String LOGIN = "redirect:/member/login";
    private static final String NO_ASSIGNMENT = "배정된 매장이 있는 관리자만 이용할 수 있습니다.";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @DaisoGetMapping("/store-manager/dashboard")
    public String dashboard(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return overview(request, response, "dashboard", "매장 대시보드", "store-manager/dashboard");
    }

    @DaisoGetMapping("/store-manager/assortment")
    public String assortment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return StoreManagerAssortmentController.list(request, response);
    }

    @DaisoGetMapping("/store-manager/performance")
    public String performance(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return overview(request, response, "performance", "매장 성과 분석", "store-manager/planned");
    }

    @DaisoGetMapping("/store-manager/settings")
    public String settings(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return overview(request, response, "settings", "매장 정보 관리", "store-manager/planned");
    }

    private String overview(HttpServletRequest request, HttpServletResponse response,
            String section, String title, String view) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        String memberId = StoreManagerAccess.memberId(request);
        if (memberId == null) {
            return LOGIN;
        }

        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
            if (manager == null) {
                connection.rollback();
                response.sendError(HttpServletResponse.SC_FORBIDDEN, NO_ASSIGNMENT);
                return null;
            }
            connection.commit();
            showManager(request, manager, section);
            request.setAttribute("storeManagerTitle", title);
            if ("dashboard".equals(section)) {
                request.setAttribute("storeManagerCheckedAt", OffsetDateTime.now(SEOUL).withNano(0));
            }
            return view;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 관리자 정보 조회 실패", error);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "매장 관리자 화면을 불러오지 못했습니다.");
            return null;
        }
    }

    static void showManager(HttpServletRequest request, StoreManagerAccess manager, String section) {
        request.setAttribute("storeManagerName", manager.storeName);
        request.setAttribute("storeManagerLoginId", manager.loginId);
        request.setAttribute("storeManagerSection", section);
    }

    @DaisoGetMapping("/store-manager/inventory")
    public String inventory(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return "history".equals(request.getParameter("mode"))
                ? history(request, response, true) : StoreManagerInventoryController.list(request, response);
    }

    @DaisoGetMapping("/store-manager/history")
    public String history(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return history(request, response, false);
    }

    private String history(HttpServletRequest request, HttpServletResponse response, boolean legacy) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        String memberId = StoreManagerAccess.memberId(request);
        if (memberId == null) return LOGIN;

        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
            if (manager == null) {
                connection.rollback();
                response.sendError(HttpServletResponse.SC_FORBIDDEN, NO_ASSIGNMENT);
                return null;
            }

            String product = ProductQueries.text(request, "product", 100);
            String type = ProductQueries.text(request, "historyType", 14);
            if (!StoreInventoryDAO.HistoryFilter.TYPES.contains(type)) throw new IllegalArgumentException();
            HistoryFilter filter = new HistoryFilter(null, null, type, "", "", false, product);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            long total = dao.countHistory(connection, manager.storeId, "", filter);
            int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = Math.min(pages, ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE));
            String path = legacy ? "/app/store-manager/inventory" : "/app/store-manager/history";
            java.util.Map<String, String> params = java.util.Map.of(
                    "mode", legacy ? "history" : "", "product", product, "historyType", type);

            showManager(request, manager, "history");
            request.setAttribute("storeManagerHistoryProduct", product);
            request.setAttribute("storeManagerHistoryType", type);
            request.setAttribute("storeManagerHistory", dao.history(connection, manager.storeId,
                    "", filter, PAGE_SIZE, (page - 1) * PAGE_SIZE));
            request.setAttribute("storeManagerHistoryPagination", new InventoryPagination(page, pages,
                    target -> ProductView.url(request.getContextPath(), path,
                            ProductView.with(params, "page", Integer.toString(target)))));

            connection.commit();
            return "store-manager/history";
        } catch (IllegalArgumentException error) {
            response.sendError(400, "이력 조회 조건을 확인해 주세요.");
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 관리자 재고 이력 조회 실패", error);
            response.sendError(503, "재고 이력을 불러오지 못했습니다.");
            return null;
        }
    }

    @DaisoGetMapping("/store-manager/pickup-orders")
    public String pickupOrders(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        String memberId = StoreManagerAccess.memberId(session);

        if (memberId == null) {
            return LOGIN;
        }

        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
            if (manager == null) {
                connection.rollback();
                response.sendError(HttpServletResponse.SC_FORBIDDEN, NO_ASSIGNMENT);
                return null;
            }
            String storeId = manager.storeId;

            showManager(request, manager, "pickup-orders");
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
        String memberId = StoreManagerAccess.memberId(session);

        if (memberId == null) {
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

        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
            if (manager == null) {
                connection.rollback();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return NO_ASSIGNMENT;
            }

            String orderNo = new OrderDAO().findReadyPickupOrderNoByToken(
                    connection, manager.storeId, pickupToken);
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
        String memberId = StoreManagerAccess.memberId(session);
        if (memberId == null) {
            return LOGIN;
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
            StoreManagerAccess manager = StoreManagerAccess.findForWrite(connection, memberId);
            if (manager == null) {
                connection.rollback();
                response.sendError(HttpServletResponse.SC_FORBIDDEN, NO_ASSIGNMENT);
                return null;
            }
            String storeId = manager.storeId;

            boolean completing = "complete".equals(action);
            String pickupToken = null;
            if (completing) {
                pickupToken = request.getParameter("pickupToken");
                if (pickupToken == null || !pickupToken.matches("[A-Za-z0-9__-]{43}")) {
                    connection.rollback();
                    response.sendError(400, "픽업QR을 다시 스캔해 주세요.");
                    return null;
                }
                boolean passwordVerified;
                try {
                    passwordVerified = new MemberService().verifyStoreManagerPassword(
                            memberId, request.getParameter("approvalPassword"));
                } catch (Exception error) {
                    connection.rollback();
                    request.getServletContext().log("픽업 승인정보 확인 실패", error);
                    response.sendError(503, "승인 정보를 확인하지 못했습니다.");
                    return null;
                }
                if (!passwordVerified) {
                    connection.rollback();
                    response.sendError(403, "매장 관리자비밀번호가 올바르지 않습니다.");
                    return null;
                }
            }
            boolean changed = new OrderDAO().advancePickupStatus(
                    connection, storeId, orderNo, completing, pickupToken);
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
}
