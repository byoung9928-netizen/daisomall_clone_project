package daiso.product.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class ProductView {
    private ProductView() {
    }

    public static String escape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String url(String context, String path, Map<String, String> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((name, value) -> {
            if (value != null && !value.isEmpty()) {
                query.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return escape(context + path + (query.length() == 0 ? "" : "?" + query));
    }

    public static Map<String, String> with(Map<String, String> params, String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(params);
        next.put(name, value);
        return next;
    }

    public static String money(int amount) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원";
    }

    public static String image(String context, ProductDTO product) {
        String value = product.getImage();
        if (daiso.product.service.ProductImageStorage.isImagePath(value)) return escape(context + "/" + value);
        if (value != null) {
            try {
                URI uri = URI.create(value);
                if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                    return escape(uri);
                }
                if (uri.getScheme() == null && uri.getRawAuthority() == null && value.startsWith("assets/")
                        && uri.normalize().getPath().startsWith("assets/")) {
                    return escape(context + "/" + uri);
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the local placeholder.
            }
        }
        return escape(context + "/assets/images/mock-product-0.svg");
    }

    public static boolean available(ProductDTO product) {
        return "AVAILABLE".equals(product.getStatus()) && product.getStockQuantity() > 0;
    }

    public static String status(ProductDTO product) {
        if ("SCHEDULED".equals(product.getStatus())) return "판매 예정";
        return available(product) ? "구매 가능" : "품절";
    }
}
