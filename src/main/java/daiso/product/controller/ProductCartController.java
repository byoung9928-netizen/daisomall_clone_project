package daiso.product.controller;

import daiso.product.service.ProductBehaviorRecorder;
import daiso.product.service.ProductBehaviorRecorder.Type;

import daiso.cart.db.CartDAO;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class ProductCartController {
    @DaisoPostMapping("/products/cart/preview")
    @DaisoResponseBody
    public String preview(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Object member = session == null ? null : session.getAttribute("member_id");
        if (!(member instanceof String) || ((String) member).isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return "로그인 후 이용해 주세요.";
        }
        String contentType = request.getContentType();
        if (contentType == null || !"application/x-www-form-urlencoded".equalsIgnoreCase(contentType.split(";", 2)[0].strip())
                || !WishlistController.validToken(request, (String) member)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }
        String[] ids = request.getParameterValues("productId");
        if (ids == null || ids.length != 1 || ids[0].isBlank() || ids[0].length() > 100
                || ids[0].codePoints().anyMatch(Character::isISOControl)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "상품 번호를 확인해 주세요.";
        }
        try {
            if (new CartDAO().addSoldOutProduct((String) member, ids[0])) {
                ProductBehaviorRecorder.record(request, Type.CART_ADD, ids[0]);
            }
            return "OK";
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return error.getMessage();
        } catch (SQLException error) {
            request.getServletContext().log("품절 상품 미리 담기 실패", error);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "장바구니에 미리 담지 못했습니다.";
        }
    }
}
