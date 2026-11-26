package daiso.support.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;

@DaisoController
public class FaqController {
    @DaisoGetMapping("/support/faq")
    public String faq(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");

        if (memberId instanceof String && !((String) memberId).isBlank()) {
            return "redirect:/order/mypage?section=faq";
        }

        return "support/faq";
    }
}