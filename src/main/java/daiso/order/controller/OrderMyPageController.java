package daiso.order.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import daiso.cart.db.CartBean;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;

@DaisoController
public class OrderMyPageController {
    @DaisoGetMapping("/order/mypage")
    public String mypage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String memberId = session == null ? null : (String) session.getAttribute("member_id");
        if (memberId == null || memberId.isBlank()) return "redirect:/member/login";

        List<Map<String,Object>> orders = new ArrayList<>();
        Enumeration<String> names = session.getAttributeNames();
        while (names.hasMoreElements()) {
            String key = names.nextElement();
            if (!key.startsWith("kakaoPending:")) continue;
            Object value = session.getAttribute(key);
            if (!(value instanceof Map)) continue;
            Map<String,Object> pending = (Map<String,Object>) value;
            synchronized (pending) {
                if (!memberId.equals(pending.get("memberId")) || !"APPROVED".equals(pending.get("status"))) continue;
                try {
                    JsonObject approval = JsonParser.parseString((String) pending.get("approvalResult")).getAsJsonObject();
                    List<CartBean> items = (List<CartBean>) pending.get("items");
                    if (items == null) continue;
                    long subtotal = 0;
                    for (CartBean item : items) subtotal += item.getItemTotal();
                    long total = ((Number) pending.get("totalAmount")).longValue();
                    Map<String,Object> order = new HashMap<>();
                    order.put("orderNo", pending.get("orderNo"));
                    order.put("items", new ArrayList<>(items));
                    Object address = pending.get("deliveryAddress");
                    order.put("address", address instanceof Map ? new HashMap<>((Map<String,String>) address) : Collections.emptyMap());
                    order.put("message", pending.get("deliveryMessage"));
                    order.put("subtotal", subtotal);
                    order.put("shipping", total - subtotal);
                    order.put("total", total);
                    String rawTime = approval.has("approved_at") ? approval.get("approved_at").getAsString() : "";
                    order.put("sortTime", rawTime);
                    String date = "", paidAt = rawTime;
                    try {
                        java.time.LocalDateTime parsed = java.time.LocalDateTime.parse(rawTime);
                        date = parsed.toLocalDate().toString();
                        paidAt = parsed.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    } catch (java.time.format.DateTimeParseException ignored) { /* 잘못된 시각은 집계에서 제외 */ }
                    order.put("date", date);
                    order.put("paidAt", paidAt);
                    orders.add(order);
                } catch (RuntimeException ignored) {
                    // 불완전한 세션 정보는 결제 성공 주문으로 표시하지 않습니다.
                }
            }
        }
        orders.sort(Comparator.comparing((Map<String,Object> order) -> (String) order.get("sortTime")).reversed());
        String requested = request.getParameter("orderNo");
        Map<String,Object> selected = null;
        if (requested != null && !requested.isBlank()) {
            for (Map<String,Object> order : orders) {
                if (requested.equals(order.get("orderNo"))) { selected = order; break; }
            }
            if (selected == null) request.setAttribute("myOrderError", "현재 회원의 주문 정보를 찾을 수 없습니다.");
        } else if (!orders.isEmpty()) selected = orders.get(0);

        String year = Integer.toString(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).getYear());
        long count = 0, amount = 0;
        for (Map<String,Object> order : orders) {
            if (((String) order.get("date")).startsWith(year + "-")) {
                count++;
                amount += ((Number) order.get("total")).longValue();
            }
        }
        request.setAttribute("myPageYear", year);
        request.setAttribute("myPagePurchaseCount", count);
        request.setAttribute("myPagePurchaseAmount", amount);
        request.setAttribute("myOrders", orders);
        request.setAttribute("myOrder", selected);
        request.setAttribute("memberName", session.getAttribute("name"));
        request.setAttribute("memberEmail", session.getAttribute("email"));
        request.setAttribute("activeNavigation", "");
        return "order/mypage-detail";
    }
}
