package daiso.product.controller;

import daiso.product.service.ProductBehaviorRecorder;
import daiso.product.service.ProductBehaviorRecorder.Type;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.product.dto.WishlistPage;
import daiso.product.service.WishlistService;

@DaisoController
public final class WishlistController {
    private static final String CSRF = "productWishlistCsrf";
    private static final String CSRF_MEMBER = "productWishlistCsrfMember";
    private static final WishlistService SERVICE = new WishlistService();
    private static final String STORAGE_MESSAGE = "찜 DB 테이블이 아직 적용되지 않아 사용할 수 없습니다.";
    private static final String FAILURE_MESSAGE = "찜 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";

    // 기존 상품 화면에서 사용하는 로그인 정보와 찜 상태 준비
    public static void prepare(HttpServletRequest request, HttpServletResponse response,
            Connection connection, Collection<String> productIds) {
        privateResponse(response);
        String memberId = memberId(request);
        request.setAttribute("wishlistLoggedIn", memberId != null);
        request.setAttribute("wishlistSelected", Set.of());
        request.setAttribute("wishlistCsrf", "");
        if (memberId == null) return;
        request.setAttribute("wishlistCsrf", token(request.getSession(false), memberId));
        try {
            if (!productIds.isEmpty()) {
                request.setAttribute("wishlistSelected", SERVICE.findSelected(connection, memberId, productIds));
            }
        } catch (SQLException error) {
            request.setAttribute("wishlistError",
                    WishlistService.isStorageMissing(error) ? STORAGE_MESSAGE : FAILURE_MESSAGE);
            if (!WishlistService.isStorageMissing(error)) {
                request.getServletContext().log("상품 화면 찜 상태 조회 실패", error);
            }
        }
    }

    // 관심상품 목록: 회원 ID는 요청 파라미터가 아닌 로그인 세션에서만 확인
    @DaisoGetMapping("/products/wishlist")
    public String list(HttpServletRequest request, HttpServletResponse response) {
        prepare(request, response, null, List.of());
        String memberId = memberId(request);
        if (memberId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            request.setAttribute("wishlistLoginRequired", true);
            return "product/wishlist";
        }
        try {
            String rawPage = parameter(request, "page", 10, false);
            int page = rawPage.isEmpty() ? 1 : Integer.parseInt(rawPage);
            WishlistPage result = SERVICE.findPage(memberId, page);
            request.setAttribute("wishlistPage", result);
            request.setAttribute("wishlistSelected", result.getItems().stream()
                    .map(item -> item.getProduct().getProductId()).collect(Collectors.toSet()));
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("wishlistError", "페이지 번호를 확인해 주세요.");
        } catch (SQLException error) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("wishlistError",
                    WishlistService.isStorageMissing(error) ? STORAGE_MESSAGE : FAILURE_MESSAGE);
            if (!WishlistService.isStorageMissing(error)) {
                request.getServletContext().log("관심상품 목록 조회 실패", error);
            }
        } catch (NamingException error) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("wishlistError", FAILURE_MESSAGE);
            request.getServletContext().log("관심상품 DB 연결 실패", error);
        }
        return "product/wishlist";
    }

    // 상태 변경은 등록과 해제를 구분한 POST만 허용
    @DaisoPostMapping("/products/wishlist/add")
    @DaisoResponseBody
    public String add(HttpServletRequest request, HttpServletResponse response) {
        return change(request, response, true);
    }

    @DaisoPostMapping("/products/wishlist/remove")
    @DaisoResponseBody
    public String remove(HttpServletRequest request, HttpServletResponse response) {
        return change(request, response, false);
    }

    private String change(HttpServletRequest request, HttpServletResponse response, boolean selected) {
        privateResponse(response);
        String memberId = memberId(request);
        if (memberId == null) return fail(response, HttpServletResponse.SC_UNAUTHORIZED, "LOGIN_REQUIRED");
        try {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String type = request.getContentType();
            if (type == null || !"application/x-www-form-urlencoded".equalsIgnoreCase(type.split(";")[0].strip())) {
                return fail(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "INVALID_REQUEST");
            }
            if (!validToken(request, memberId)) {
                return fail(response, HttpServletResponse.SC_FORBIDDEN, "CSRF_INVALID");
            }
            String productId = parameter(request, "productId", 100, true);
            if (SERVICE.setSelected(memberId, productId, selected)) {
                ProductBehaviorRecorder.record(request, selected ? Type.WISHLIST_ADD : Type.WISHLIST_REMOVE, productId);
            }
            return selected ? "ADDED" : "REMOVED";
        } catch (IllegalArgumentException error) {
            return fail(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PRODUCT");
        } catch (SQLException error) {
            if (WishlistService.isStorageMissing(error)) {
                return fail(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "STORAGE_NOT_READY");
            }
            request.getServletContext().log("찜 변경 실패", error);
            return fail(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SAVE_FAILED");
        } catch (NamingException | java.io.UnsupportedEncodingException error) {
            request.getServletContext().log("찜 요청 처리 실패", error);
            return fail(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SAVE_FAILED");
        }
    }

    // 기존 관리자와 같은 세션 토큰 방식. 회원이 바뀌면 토큰도 교체
    private static String token(HttpSession session, String memberId) {
        synchronized (session) {
            Object value = session.getAttribute(CSRF);
            if (!(value instanceof String) || !memberId.equals(session.getAttribute(CSRF_MEMBER))) {
                value = UUID.randomUUID().toString();
                session.setAttribute(CSRF, value);
                session.setAttribute(CSRF_MEMBER, memberId);
            }
            return (String) value;
        }
    }

    static boolean validToken(HttpServletRequest request, String memberId) {
        HttpSession session = request.getSession(false);
        Object expected = session.getAttribute(CSRF);
        String[] submitted = request.getParameterValues("csrf");
        return memberId.equals(session.getAttribute(CSRF_MEMBER)) && expected instanceof String
                && submitted != null && submitted.length == 1 && submitted[0] != null
                && submitted[0].length() <= 64 && MessageDigest.isEqual(
                        ((String) expected).getBytes(StandardCharsets.UTF_8),
                        submitted[0].getBytes(StandardCharsets.UTF_8));
    }

    // 인증 및 입력 도우미
    private static String memberId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("member_id");
        return value instanceof String && !((String) value).isBlank() ? (String) value : null;
    }

    private static String parameter(HttpServletRequest request, String name, int max, boolean required) {
        String[] values = request.getParameterValues(name);
        if (values == null && !required) return "";
        if (values == null || values.length != 1 || values[0] == null) {
            throw new IllegalArgumentException("요청값을 확인해 주세요.");
        }
        String value = values[0];
        if ((required && value.isBlank()) || !value.equals(value.strip())
                || value.codePointCount(0, value.length()) > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("요청값을 확인해 주세요.");
        }
        return value;
    }

    private static void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Vary", "Cookie");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private static String fail(HttpServletResponse response, int status, String code) {
        response.setStatus(status);
        return code;
    }
}
