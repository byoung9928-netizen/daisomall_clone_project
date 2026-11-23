package daiso.product.controller;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

public final class ProductCatalogFacets {
    private static final String RESOURCE = "/WEB-INF/catalog/official-product-facets.json";
    private static final List<Choice> DELIVERY = List.of(
            new Choice("", "전체 배송 유형"), new Choice("parcel", "택배배송"),
            new Choice("pickup", "매장픽업"), new Choice("today", "오늘배송"), new Choice("bulk", "대량주문"));
    private static final Gson JSON = new Gson();
    private volatile Catalog data;

    Selection select(ServletContext context, String category, HttpServletRequest request) {
        Catalog catalog = catalog(context);
        if (catalog.products.isEmpty()) return Selection.EMPTY;
        List<Group> groups = catalog.categories.getOrDefault(category, List.of());
        String delivery = request.getParameter("delivery");
        String selectedDelivery = DELIVERY.stream().anyMatch(choice -> choice.value.equals(delivery)) ? delivery : "";
        String[] requested = request.getParameterValues("attr");
        Set<String> keys = new LinkedHashSet<>();
        if (requested != null && requested.length <= 64) {
            for (String value : requested) {
                if (value.length() <= 1800) keys.addAll(Arrays.asList(value.split(",")));
            }
        }

        Set<String> selected = new LinkedHashSet<>();
        Map<String, Set<String>> byGroup = new LinkedHashMap<>();
        for (Group group : groups) {
            for (Choice choice : group.choices) {
                if (choice.available && keys.contains(choice.value)) {
                    selected.add(choice.value);
                    byGroup.computeIfAbsent(group.id, ignored -> new LinkedHashSet<>()).add(choice.value);
                }
            }
        }
        List<String> codes = new ArrayList<>();
        if (!selectedDelivery.isEmpty() || !selected.isEmpty()) {
            catalog.products.forEach((code, item) -> {
                if (selectedDelivery.isEmpty() || item.delivery.contains(selectedDelivery)) {
                    boolean matches = byGroup.values().stream().allMatch(options ->
                            options.stream().anyMatch(item.attributes::contains));
                    if (matches) codes.add(code);
                }
            });
        }
        String matchingCodes = selectedDelivery.isEmpty() && selected.isEmpty() ? null : JSON.toJson(codes);
        return new Selection(DELIVERY, selectedDelivery, groups, selected, matchingCodes);
    }

    private Catalog catalog(ServletContext context) {
        Catalog cached = data;
        if (cached != null) return cached;
        synchronized (this) {
            if (data == null) data = load(context);
            return data;
        }
    }

    private static Catalog load(ServletContext context) {
        try (InputStream input = context.getResourceAsStream(RESOURCE)) {
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    Catalog catalog = JSON.fromJson(reader, Catalog.class);
                    if (catalog != null) return catalog;
                }
            }
        } catch (IOException | JsonParseException error) {
            context.log("공식 상품 필터 자료를 읽지 못했습니다.", error);
        }
        return new Catalog();
    }

    private static final class Catalog {
        Map<String, List<Group>> categories = Map.of();
        Map<String, Product> products = Map.of();
    }

    private static final class Product {
        Set<String> delivery = Set.of();
        Set<String> attributes = Set.of();
    }

    public static final class Choice {
        public final String value;
        public final String label;
        public final boolean available;

        private Choice(String value, String label) {
            this.value = value;
            this.label = label;
            this.available = true;
        }
    }

    public static final class Group {
        private String id;
        public String label;
        public List<Choice> choices;
    }

    public static final class Selection {
        static final Selection EMPTY = new Selection(List.of(), "", List.of(), Set.of(), null);
        public final List<Choice> deliveries;
        public final String delivery;
        public final List<Group> groups;
        public final Set<String> selected;
        final String matchingCodes;

        private Selection(List<Choice> deliveries, String delivery, List<Group> groups, Set<String> selected, String matchingCodes) {
            this.deliveries = deliveries;
            this.delivery = delivery;
            this.groups = groups;
            this.selected = Set.copyOf(selected);
            this.matchingCodes = matchingCodes;
        }
    }
}
