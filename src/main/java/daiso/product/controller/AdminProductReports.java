package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;

import daiso.admin.report.AdminReport;

public final class AdminProductReports {
    private AdminProductReports() {
    }

    // ===== 상품 목록과 동일한 검색·정렬 조건으로 엑셀 데이터 조회 =====
    public static AdminReport products(HttpServletRequest request) throws SQLException, NamingException {
        AdminProductFilter filter = AdminProductFilter.read(request, AdminProductController.STATUSES);
        AdminReport report = new AdminReport("상품 관리",
                List.of("상품 ID", "품번", "상품명", "가격(원)", "판매 상태", "온라인 재고"),
                "판매 상태별 상품 수", "판매 상태", "상품 수", new LinkedHashMap<>());
        try (Connection con = ProductQueries.connect()) {
            for (ProductDTO product : new ProductDAO().findAdminProductsForExport(con, filter)) {
                String raw = product.getStatus();
                String status = raw == null ? "상태 확인 필요" : AdminProductController.STATUSES.getOrDefault(raw, raw);
                report.rows.add(new Object[] {product.getProductId(), product.getProductCode(),
                        product.getName(), product.getPrice(), status, product.getStockQuantity()});
                report.chart.merge(status, 1L, Long::sum);
            }
            con.commit();
        }
        return report;
    }

    // ===== 온라인 재고 화면의 상태 분류와 재고 임계값을 재사용 =====
    public static AdminReport inventory(HttpServletRequest request) throws SQLException, NamingException {
        AdminReport report = new AdminReport("온라인 재고",
                List.of("상품 ID", "상품명", "온라인 재고", "재고 상태"),
                "재고 상태별 상품 수", "재고 상태", "상품 수", new LinkedHashMap<>());
        try (Connection con = ProductQueries.connect()) {
            ProductDAO dao = new ProductDAO();
            AdminProductFilter filter = OnlineInventoryController.readInventoryFilter(request, dao, con);
            for (ProductDTO product : dao.findAdminProductsForExport(con, filter)) {
                String status = OnlineStockState.of(product).label;
                report.rows.add(new Object[] {product.getProductId(), product.getName(), product.getStockQuantity(), status});
                report.chart.merge(status, 1L, Long::sum);
            }
            con.commit();
        }
        return report;
    }
}
