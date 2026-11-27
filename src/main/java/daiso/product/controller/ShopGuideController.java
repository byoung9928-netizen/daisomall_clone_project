package daiso.product.controller;

import javax.servlet.http.HttpServletRequest;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;

@DaisoController
public class ShopGuideController {

    @DaisoGetMapping("/guides/delivery")
    public String delivery(HttpServletRequest request) {
        request.setAttribute("shopGuide", "delivery");
        return "shop/guide";
    }

    @DaisoGetMapping("/guides/events")
    public String events(HttpServletRequest request) {
        request.setAttribute("shopGuide", "events");
        return "shop/guide";
    }

    @DaisoGetMapping("/guides/partner-card")
    public String partnerCard(HttpServletRequest request) {
        request.setAttribute("shopGuide", "partner-card");
        return "shop/guide";
    }
}