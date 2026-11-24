package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Location;
import daiso.product.dao.StoreInventoryDAO.StockAvailability;

@DaisoController
public final class StoreFinderController {
    public static final String LOCATIONS = "/app/stores/locations";
    public static final String PRODUCTS = "/app/stores/products";
    private static final String VIEW = "product/store-finder";
    private static final int PAGE_SIZE = 12;
    private final StoreInventoryDAO stores = new StoreInventoryDAO();
    private final ProductDAO products = new ProductDAO();

    @DaisoGetMapping("/stores/locations")
    public String locations(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("locationFinder", true);
        try {
            String mapsClientId = javax.naming.InitialContext.doLookup(
                    "java:comp/env/naverMapsClientId");
            request.setAttribute("naverMapsClientId", mapsClientId);
        } catch (NamingException e) {
            request.setAttribute("naverMapsClientId", "");
        }
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 100);
            List<Location> matches = matches(stores.locations(connection), keyword);
            request.setAttribute("storeFinder", new StoreFinderView(request.getContextPath(), keyword, matches));
            connection.commit();
        } catch (IllegalArgumentException error) {
            return message(request, response, 400, "검색어를 100자 이내로 입력해 주세요.");
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
        return VIEW;
    }

    @DaisoGetMapping("/stores/products")
    public String products(HttpServletRequest request, HttpServletResponse response) {
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 100);
            String storeKeyword = ProductQueries.text(request, "storeQuery", 100);
            String productId = ProductQueries.text(request, "product", 100);
            String storeId = ProductQueries.text(request, "store", 40);
            String picker = ProductQueries.text(request, "picker", 10);
            int page = ProductQueries.integer(request, "page", 1, 1, 1000);
            if (!List.of("", "product", "store").contains(picker)) throw new IllegalArgumentException();
            ProductDTO product = productId.isEmpty() ? null : products.findVisibleById(connection, productId);
            if (!productId.isEmpty() && product == null) {
                return message(request, response, 404, "현재 조회할 수 없는 상품입니다.");
            }

            List<Location> locations = storeId.isEmpty() && !"store".equals(picker)
                    ? List.of() : stores.locations(connection);
            Location selected = locations.stream().filter(store -> store.id.equals(storeId)).findFirst().orElse(null);
            if (!storeId.isEmpty() && selected == null) {
                return message(request, response, 404, "현재 조회할 수 없는 매장입니다.");
            }
            List<Location> storeResults = "store".equals(picker) ? matches(locations, storeKeyword) : List.of();
            CatalogPage productResults = null;
            if ("product".equals(picker) && !keyword.isEmpty()) {
                CatalogFilter filter = new CatalogFilter();
                filter.keyword = keyword;
                filter.limit = PAGE_SIZE;
                filter.offset = (page - 1) * PAGE_SIZE;
                productResults = products.findCatalog(connection, filter);
            }
            StockAvailability availability = product == null || selected == null ? null
                    : StoreInventoryDAO.forInventory(connection).availability(connection, selected.id, product.getProductId());
            request.setAttribute("storeFinder", new StoreFinderView(request.getContextPath(), keyword, storeKeyword,
                    picker, product, selected, availability, storeResults, productResults, page, PAGE_SIZE));
            connection.commit();
        } catch (IllegalArgumentException error) {
            return message(request, response, 400, "검색 조건을 다시 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
        return VIEW;
    }
    @daiso.common.annotation.DaisoPostMapping("/stores/chat")
    @daiso.common.annotation.DaisoResponseBody
    public String chat(HttpServletRequest request, HttpServletResponse response)
            throws java.io.UnsupportedEncodingException {

        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        javax.servlet.http.HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("member_id") == null) {
            response.setStatus(401);
            return "로그인한 뒤 이용해 주세요.";
        }

        if (!"stock-chat".equals(request.getHeader("X-Daiso-Request"))) {
            response.setStatus(403);
            return "홈 화면에서 다시 요청해 주세요.";
        }

        String question;
        try {
            question = ProductQueries.text(request, "question", 200);
        } catch (IllegalArgumentException e) {
            return "질문은 200자 이내로 입력해 주세요.";
        }
        if (question.isEmpty()) {
            return "매장 이름과 상품 이름을 입력해 주세요.";
        }

        synchronized (session) {
            long now = System.currentTimeMillis();
            Long last = (Long) session.getAttribute("stockChatLastRequest");
            Integer count = (Integer) session.getAttribute("stockChatRequestCount");

            if (last != null && now - last < 5_000L) {
                return "5초 뒤에 다시 질문해 주세요.";
            }
            if (count != null && count >= 15) {
                return "이번 로그인에서 사용할 수 있는 질문 횟수를 모두 사용했어요.";
            }

            session.setAttribute("stockChatLastRequest", now);
            session.setAttribute("stockChatRequestCount", count == null ? 1 : count + 1);
        }

        try {
        	String history = request.getParameter("history");
        	if (history != null && history.length() > 1600) {
        	    return "대화가 너무 길어요. 페이지를 새로 열고 다시 질문해 주세요.";
        	}

        	com.google.gson.JsonObject terms =
        			new GeminiStockQuestionClient().parseQuestion(question, history);

        	if ("general".equals(terms.get("kind").getAsString())) {
        	    String answer = terms.get("answer").getAsString().strip();
        	    return answer.isEmpty() ? "답변을 만들지 못했어요. 다르게 질문해 주세요." : answer;
        	}
            String storeQuery = terms.get("store").getAsString().strip();
            String productQuery = terms.get("product").getAsString().strip();

            if (storeQuery.isEmpty() || productQuery.isEmpty()
                    || storeQuery.length() > 100 || productQuery.length() > 100) {
                return "매장명과 상품명을 함께 알려주세요. 예: 홍대점에 네임펜 재고 있어?";
            }

            try (Connection connection = ProductQueries.connect()) {
                List<Location> storesFound = matches(stores.locations(connection), storeQuery);
                if (storesFound.isEmpty()) {
                    return "일치하는 매장을 찾지 못했어요. 예: 홍대점, 강남점, 서면점, 해운대점";
                }
                if (storesFound.size() > 1) {
                    return "어느 매장인가요? "
                            + storesFound.stream().limit(5).map(store -> store.name)
                                    .collect(Collectors.joining(", "))
                            + " 중 매장명과 상품명을 함께 적어 주세요.";
                }

                Location store = storesFound.get(0);
                CatalogFilter filter = new CatalogFilter();
                filter.keyword = productQuery;
                filter.limit = 5;
                CatalogPage found = products.findCatalog(connection, filter);

                if (found.products.isEmpty()) {
                    return "일치하는 상품을 찾지 못했어요. 짧은 상품명이나 품번으로 다시 질문해 주세요.";
                }
                if (found.total > 1) {
                    return "어느 상품인가요? 아래 후보 중 상품명이나 품번을 매장명과 함께 적어 주세요.\n"
                            + found.products.stream()
                                    .map(product -> "• " + product.getName()
                                            + " (품번 " + product.getProductCode() + ")")
                                    .collect(Collectors.joining("\n"));
                }

                ProductDTO product = found.products.get(0);
                Integer quantity = stores.quantity(connection, store.id, product.getProductId());
                connection.commit();

                String stock = quantity == null ? "이 매장에서 취급하지 않아요."
                        : quantity == 0 ? "현재 품절이에요." : "재고가 " + quantity + "개 있어요.";

                return store.name + " · " + product.getName() + "\n" + stock
                        + "\n학습용 가상 매장 재고입니다.";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.setStatus(503);
            return "조회가 중단됐어요. 잠시 후 다시 시도해 주세요.";
        } catch (Exception e) {
            request.getServletContext().log("매장 재고 챗봇 조회 실패", e);
            response.setStatus(502);
            return "지금은 재고를 조회할 수 없어요. 잠시 후 다시 시도해 주세요.";
        }
    }
    
    private static List<Location> matches(List<Location> locations, String keyword) {
        String[] terms = keyword.toLowerCase(Locale.ROOT).split("\\s+");
        return locations.stream().filter(store -> {
            String name = (store.name + " " + store.city + " " + store.id).toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (!name.contains(term)) return false;
            }
            return true;
        }).collect(Collectors.toUnmodifiableList());
    }

    private static String unavailable(HttpServletRequest request, HttpServletResponse response, Exception error) {
        request.getServletContext().log("고객 매장 조회 실패", error);
        return message(request, response, 500, "매장 정보를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static String message(HttpServletRequest request, HttpServletResponse response, int status, String text) {
        response.setStatus(status);
        request.setAttribute("storeFinderError", text);
        return VIEW;
    }
}
