package daiso.order.controller;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import daiso.common.annotation.*;
import daiso.order.db.OrderCancelViewDAO;

@DaisoController
public class OrderCancelPageController {
    @DaisoGetMapping("/order/cancel/form")
    public String form(HttpServletRequest request) throws SQLException {
        HttpSession session=request.getSession(false);
        String memberId=session==null?null:(String)session.getAttribute("member_id");
        if(memberId==null||memberId.isBlank()) return "redirect:/member/login";
        String orderNo=request.getParameter("orderNo");
        if(orderNo==null||orderNo.isBlank()||orderNo.length()>100) return "redirect:/order/mypage";
        Map<String,Object> order=new OrderCancelViewDAO().findOrder(memberId,orderNo);
        if(order==null) request.setAttribute("cancelPageError","본인의 주문을 찾을 수 없습니다.");
        else if(!"PAID".equals(order.get("status"))||!"APPROVED".equals(order.get("paymentStatus"))) {
            request.setAttribute("cancelPageError","현재 취소 신청 가능한 결제완료 주문이 아닙니다.");
        } else request.setAttribute("cancelOrder",order);
        if(session.getAttribute("cancelCsrf")==null)session.setAttribute("cancelCsrf",UUID.randomUUID().toString());
        request.setAttribute("cancelCsrf",session.getAttribute("cancelCsrf"));
        prepareMemberSummary(request,session,memberId);
        return "order/cancel-form";
    }
    @DaisoGetMapping("/order/cancel/complete")
    public String complete(HttpServletRequest request) throws SQLException {
        HttpSession session=request.getSession(false);
        String memberId=session==null?null:(String)session.getAttribute("member_id");
        if(memberId==null||memberId.isBlank())return "redirect:/member/login";
        String orderNo=request.getParameter("orderNo");
        Map<String,Object> order=orderNo==null||orderNo.isBlank()?null:new OrderCancelViewDAO().findOrder(memberId,orderNo);
        if(order==null||!"CANCELLED".equals(order.get("status"))||!"CANCELLED".equals(order.get("paymentStatus"))) {
            request.setAttribute("cancelCompleteError","취소 완료된 주문을 확인할 수 없습니다.");
        }
        prepareMemberSummary(request,session,memberId);
        return "order/cancel-complete";
    }
    private void prepareMemberSummary(HttpServletRequest request,HttpSession session,String memberId) throws SQLException {
        int year=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).getYear();
        Map<String,Object> summary=new OrderCancelViewDAO().findSummary(memberId,year);
        request.setAttribute("memberName",session.getAttribute("name"));
        request.setAttribute("memberEmail",session.getAttribute("email"));
        request.setAttribute("myPageYear",Integer.toString(year));
        request.setAttribute("myPagePurchaseCount",summary.get("count"));
        request.setAttribute("myPagePurchaseAmount",summary.get("amount"));
        request.setAttribute("activeNavigation","");
    }
}
