package daiso.product.controller;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;

/** 상품 화면용으로 정리한 공식 사실 정보와 설명 사진을 서버에서 읽는다. */
public final class OfficialProductDetails {
    private static final String RESOURCE = "/WEB-INF/catalog/official-product-details.json";
    private volatile Map<String, Entry> entries;

    Entry find(ServletContext context, ProductDTO product) {
        if (!"REFERENCE".equals(product.getSourceKind())) return null;
        Entry entry = entries(context).get(product.getProductId());
        return entry != null && Objects.equals(entry.localCode, product.getProductCode()) ? entry : null;
    }

    private Map<String, Entry> entries(ServletContext context) {
        Map<String, Entry> current = entries;
        if (current != null) return current;
        synchronized (this) {
            if (entries == null) entries = load(context);
            return entries;
        }
    }

    private static Map<String, Entry> load(ServletContext context) {
        try (InputStream input = context.getResourceAsStream(RESOURCE)) {
            if (input == null) return Map.of();
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                Map<String, Entry> parsed = new Gson().fromJson(reader,
                        new TypeToken<Map<String, Entry>>() { }.getType());
                return parsed == null ? Map.of() : Map.copyOf(parsed);
            }
        } catch (IOException | JsonParseException error) {
            context.log("공식 상품 상세 자료를 읽지 못했습니다.", error);
            return Map.of();
        }
    }

    public static final class Entry {
        private String source;
        private String sku;
        private String localCode;
        private Map<String, String> facts;
        private List<String> detailPhotos;
        private List<String> photos;

        public String getSource() {
            return allowed(source, false) ? source : "";
        }

        public String getSku() { return sku; }

        public Map<String, String> getFacts() {
            return facts == null ? Map.of() : facts;
        }

        public List<String> getDetailPhotos() {
            return safeImages(detailPhotos);
        }

        public List<String> getRepresentativePhotos() {
            return safeImages(photos);
        }

        private static List<String> safeImages(List<String> urls) {
            return urls == null ? List.of() : urls.stream().filter(url -> allowed(url, true))
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    private static boolean allowed(String value, boolean image) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1) {
                return false;
            }
            if (image) return "cdn.daisomall.co.kr".equals(uri.getHost()) && uri.getPath().startsWith("/file/");
            return "www.daisomall.co.kr".equals(uri.getHost()) || "daisomall.co.kr".equals(uri.getHost())
                    || "prdm.daisomall.co.kr".equals(uri.getHost()) || "www.daiso.co.kr".equals(uri.getHost());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
