package daiso.admin.report;

import java.sql.Timestamp;
import javax.naming.NamingException;
import daiso.member.controller.AdminMemberInfo;
import daiso.member.controller.MemberDAO;
import daiso.member.controller.AdminDashboardDAO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import daiso.order.db.AdminOrderDAO;
import daiso.order.db.OrderBean;
import daiso.order.db.ReturnDAO;

public final class AdminReport {
    public final String title;
    public final List<String> headers;
    public final List<Object[]> rows = new ArrayList<>();
    public final String chartTitle;
    public final String categoryLabel;
    public final String valueLabel;
    public final Map<String, Long> chart;

    public AdminReport(String title, List<String> headers, String chartTitle,
            String categoryLabel, String valueLabel, Map<String, Long> chart) {
        this.title = title;
        this.headers = headers;
        this.chartTitle = chartTitle;
        this.categoryLabel = categoryLabel;
        this.valueLabel = valueLabel;
        this.chart = chart;
    }
    //대시보드 엑셀: 화면과 같은 DAO로 운영현황을 조회
    static AdminReport dashboard()throws NamingException, java.sql.SQLException{
    	AdminDashboardDAO.Summary summary = new AdminDashboardDAO().findSummary();
    	
    	AdminReport report = new AdminReport(
    			"대시보드",
    			List.of("항목","값","단위"),
    			"전체 주문과 배송 대기 주문",
    			"주문 구분",
    			"주문 건수",
    			new LinkedHashMap<>());
    	report.rows.add(new Object[] {"전체 상품", summary.productCount, "개"});
    	report.rows.add(new Object[] {"전체 카테고리", summary.categoryCount, "개"});
    	report.rows.add(new Object[] {"전체 주문", summary.orderCount, "건"});
    	report.rows.add(new Object[] {"결제된 주문 금액", summary.paidAmount, "원"});
    	report.rows.add(new Object[] {"배송 처리 대기", summary.awaitingShipmentCount, "건"});
    	report.rows.add(new Object[] {"온라인 품절 상품", summary.onlineSoldOutCount, "종"});
    	
    	// 차트에는 단위가 같은 주문 건수만 넣는다.
    	report.chart.put("전체 주문", summary.orderCount);
    	report.chart.put("배송 처리 대기", summary.awaitingShipmentCount);
    	return report;
    }
    
    //사용자 관리 엑셀: 검색어와 회원상태 필터 적용
    static AdminReport users(String query, String status) throws NamingException, java.sql.SQLException{
    	String keyword = query == null ?"":query.trim();
    	if(keyword.length()>120){
    		throw new IllegalArgumentException("검색어는 120자 이내로 입력해주세요.");
    	}
    	AdminReport report = new AdminReport(
    			"사용자 관리",
    			List.of("회원번호","로그인 ID","권한","회원 상태", "가입일"),
    			"회원 상태별 인원",
    			"회원 상태",
    			"인원",
    			new LinkedHashMap<>());
    	for (AdminMemberInfo member : new MemberDAO().findAdminMembers(keyword, status)) {
    		String state = member.getStatus();
    		String label = "ACTIVE".equals(state)?"활성"
    					:"SUSPENDED".equals(state)?"정지":"기타";
    		
    		report.rows.add(new Object[]{
    			member.getMemberId(),member.getLoginId(),
    			member.getRole(), label,member.getCreatedAt()
    		});
    		report.chart.merge(label, 1L,Long::sum);
    	}
    	return report;
    }
    
    // ===== 메뉴별 조회 결과를 엑셀 데이터와 차트용 집계로 변환 =====
    static AdminReport orders(String query, boolean awaitingShipment) throws java.sql.SQLException {
        AdminReport report = new AdminReport("주문 관리",
                List.of("주문번호", "주문 상품", "주문 상태", "결제 상태", "총 주문 금액(원)", "주문 일시"),
                "일별 주문 건수", "주문일", "주문 건수", new TreeMap<>());
        for (OrderBean order : new AdminOrderDAO().getOrdersForExport(query, awaitingShipment)) {
            Timestamp created = order.getCreatedAt();
            report.rows.add(new Object[] {order.getOrderNo(), order.getProductSummary(),
                    order.getStatus(), order.getPaymentStatus(), order.getTotalAmount(), created});
            if (created != null) {
                String date = created.toLocalDateTime().toLocalDate().toString();
                report.chart.merge(date, 1L, Long::sum);
            }
        }
        return report;
    }

    static AdminReport returns() throws java.sql.SQLException {
        AdminReport report = new AdminReport("반품·환불 관리",
                List.of("신청번호", "주문번호", "주문 상품", "처리 상태", "환불 예정 금액(원)", "신청 일시", "처리 일시"),
                "반품 처리 상태별 건수", "처리 상태", "반품 건수", new LinkedHashMap<>());
        Map<String, String> labels = Map.of("REQUESTED", "반품접수", "APPROVED", "반품승인",
                "REFUNDING", "환불처리중", "REFUNDED", "환불완료", "REJECTED", "반품반려");
        for (Map<String, Object> item : new ReturnDAO().getReturnsForExport()) {
            String raw = (String) item.get("status");
            String status = raw == null ? "상태 확인 필요" : labels.getOrDefault(raw, raw);
            report.rows.add(new Object[] {item.get("returnId"), item.get("orderNo"), item.get("productSummary"),
                    status, item.get("refundAmount"), item.get("requestedAt"), item.get("processedAt")});
            report.chart.merge(status, 1L, Long::sum);
        }
        return report;
    }

    static AdminReport sales(LocalDate from, LocalDate through) throws java.sql.SQLException {
        AdminReport report = new AdminReport("판매순위",
                List.of("순위", "상품 ID", "상품명", "판매수량", "상품 매출(원)"),
                "판매수량 상위 10개 상품", "상품", "판매수량", new LinkedHashMap<>());
        int rank = 0;
        for (Object[] item : new AdminSalesDAO().list(from, through)) {
            report.rows.add(new Object[] {++rank, item[0], item[1], item[2], item[3]});
            if (rank <= 10) report.chart.put(rank + ". " + item[1], ((Number) item[2]).longValue());
        }
        return report;
    }
}
