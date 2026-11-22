package daiso.admin.report;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.AdminProductReports;

@DaisoController
public class AdminExcelController {
    private static final String EXPORT = "/app/admin/export";
    private static final Map<String, List<String>> FILTERS = Map.of(
            "orders", List.of("query", "filter"),
            "products", List.of("query", "filterCategory", "filterStatus", "filterStock", "stockQuantity", "sort"),
            "inventory", List.of("q", "category", "state", "sort"),
            "returns", List.of(),
            "sales", List.of("from", "through"));

    // ===== 공통 헤더 버튼: 현재 메뉴와 화면에 적용된 검색 조건만 전달 =====
    public static String downloadUrl(HttpServletRequest request) {
        Object active = request.getAttribute("adminSection");
        if (!isAdmin(request) || !(active instanceof String) || !FILTERS.containsKey(active)) return null;

        String section = (String) active;
        StringBuilder url = new StringBuilder(request.getContextPath()).append(EXPORT).append("?section=").append(section);
        for (String name : FILTERS.get(section)) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                url.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return url.toString();
    }

    // ===== 공통 다운로드 주소: 권한 검사, 메뉴 선택, 엑셀 파일 응답 =====
    @DaisoGetMapping("/admin/export")
    public String download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isAdmin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        String section = request.getParameter("section");
        if (section == null || !FILTERS.containsKey(section)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "엑셀을 내려받을 메뉴를 확인해 주세요.");
            return null;
        }

        AdminReport report;
        byte[] workbook;
        try {
            report = reportFor(section, request);
            workbook = new AdminExcelWriter().write(report);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "엑셀 검색 조건을 확인해 주세요.");
            return null;
        } catch (SQLException | NamingException | IOException | IllegalStateException e) {
            request.getServletContext().log("관리자 엑셀 생성 실패", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "엑셀 파일을 생성하지 못했습니다.");
            return null;
        }

        String filename = URLEncoder.encode(report.title + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + section + ".xlsx\"; filename*=UTF-8''" + filename);
        response.setHeader("Cache-Control", "no-store");
        response.setContentLength(workbook.length);
        response.getOutputStream().write(workbook);
        response.flushBuffer();
        return null;
    }

    // ===== 메뉴 추가 시 FILTERS와 이 메서드에 조회를 연결 =====
    private AdminReport reportFor(String section, HttpServletRequest request) throws SQLException, NamingException {
        switch (section) {
            case "orders":
                return AdminReport.orders(request.getParameter("query"),
                        "awaiting-shipment".equals(request.getParameter("filter")));
            case "products":
                return AdminProductReports.products(request);
            case "inventory":
                return AdminProductReports.inventory(request);
            case "returns":
                return AdminReport.returns();
            case "sales":
                LocalDate[] range = salesRange(request);
                return AdminReport.sales(range[0], range[1]);
            default:
                throw new IllegalArgumentException("지원하지 않는 메뉴입니다.");
        }
    }

    // ===== 판매순위 화면과 다운로드가 같은 기간·집계 기준을 사용 =====
    @DaisoGetMapping("/admin/sales")
    public String sales(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isAdmin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        request.setAttribute("adminSection", "sales");
        try {
            LocalDate[] range = salesRange(request);
            request.setAttribute("salesFrom", range[0]);
            request.setAttribute("salesThrough", range[1]);
            request.setAttribute("salesRows", AdminReport.sales(range[0], range[1]).rows);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("salesError", "시작일과 종료일을 확인해 주세요.");
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.getServletContext().log("판매순위 조회 실패", e);
            request.setAttribute("salesError", "판매순위를 불러오지 못했습니다.");
        }
        return "admin/sales";
    }

    private static LocalDate[] salesRange(HttpServletRequest request) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            LocalDate through = readDate(request.getParameter("through"), today);
            LocalDate from = readDate(request.getParameter("from"), through.minusDays(29));
            if (from.isAfter(through) || from.getYear() < 1000 || through.getYear() > 9998) {
                throw new IllegalArgumentException("판매순위 조회 기간을 확인해 주세요.");
            }
            return new LocalDate[] {from, through};
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("판매순위 날짜 형식을 확인해 주세요.", e);
        }
    }

    private static LocalDate readDate(String value, LocalDate fallback) {
        return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
    }

    private static boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("member_id") != null
                && "ADMIN".equals(session.getAttribute("role"));
    }
}
