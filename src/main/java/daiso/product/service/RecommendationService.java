package daiso.product.service;

import daiso.product.controller.ProductDAO.Category;
import daiso.product.controller.ProductDTO;
import daiso.product.controller.ProductView;
import daiso.product.dao.RecommendationActivityDAO.Kind;
import daiso.product.dao.RecommendationActivityDAO.Signal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecommendationService {
    public static final int HISTORY_DAYS = 90;
    private static final Map<Kind, Integer> POINTS = Map.of(
            Kind.VIEW, 1, Kind.WISHLIST, 4, Kind.CART, 7, Kind.PURCHASE, 12);
    private static final Map<Kind, String> REASONS = Map.of(
            Kind.VIEW, "최근 본 상품과 비슷해요",
            Kind.WISHLIST, "찜한 상품과 비슷해요",
            Kind.CART, "장바구니에 담은 상품과 비슷해요",
            Kind.PURCHASE, "구매하신 상품과 비슷해요");
    private static final double SIGNAL_CAP = 16;
    private static final int MAX_SEEDS = 48;

    public List<Interest> interests(Collection<Signal> signals, LocalDate today) {
        Map<String, EnumMap<Kind, Signal>> grouped = new HashMap<>();
        for (Signal signal : signals) {
            if (weight(signal, today) <= 0) continue;
            grouped.computeIfAbsent(signal.productId, id -> new EnumMap<>(Kind.class))
                    .merge(signal.kind, signal, (old, next) ->
                            old.date == null || (next.date != null && next.date.isAfter(old.date)) ? next : old);
        }
        List<Interest> result = new ArrayList<>();
        grouped.forEach((id, byKind) -> {
            double sum = 0;
            double strongest = 0;
            Kind primary = Kind.VIEW;
            for (Signal signal : byKind.values()) {
                double points = POINTS.get(signal.kind) * weight(signal, today);
                sum += points;
                if (points > strongest) {
                    strongest = points;
                    primary = signal.kind;
                }
            }
            Signal purchase = byKind.get(Kind.PURCHASE);
            boolean recentlyPurchased = purchase != null && !purchase.date.isBefore(today.minusDays(30));
            result.add(new Interest(id, Math.min(sum, SIGNAL_CAP), primary, recentlyPurchased));
        });
        result.sort(Comparator.comparingDouble((Interest i) -> i.score).reversed().thenComparing(i -> i.id));
        return List.copyOf(result.subList(0, Math.min(result.size(), MAX_SEEDS)));
    }

    private static double weight(Signal signal, LocalDate today) {
        if (signal.date == null) return signal.kind == Kind.CART ? .4 : 0;
        long days = ChronoUnit.DAYS.between(signal.date, today);
        if (days < 0 || days > HISTORY_DAYS) return 0;
        if (days <= 7) return 1;
        return days <= 30 ? .7 : .4;
    }

    public Scope scope(List<Interest> interests, List<ProductDTO> seeds, List<Category> categories) {
        Map<String, Category> tree = index(categories);
        Map<String, ProductDTO> products = byId(seeds);
        Map<String, Double> rootScores = new HashMap<>();
        for (Interest interest : interests) {
            ProductDTO seed = products.get(interest.id);
            if (seed == null) continue;
            String root = root(tree.get(seed.getCategoryId()), tree);
            if (root != null) rootScores.merge(root, interest.score, Double::sum);
        }
        List<String> roots = rootScores.keySet().stream()
                .sorted(Comparator.<String>comparingDouble(rootScores::get).reversed().thenComparing(id -> id))
                .limit(5).collect(Collectors.toList());
        Set<String> leaves = new LinkedHashSet<>();
        Set<String> parents = new LinkedHashSet<>();
        for (ProductDTO seed : seeds) {
            Category category = tree.get(seed.getCategoryId());
            if (category != null && roots.contains(root(category, tree))) {
                leaves.add(category.id);
                if (category.parentId != null) parents.add(category.parentId);
            }
        }
        return new Scope(roots, List.copyOf(leaves), List.copyOf(parents));
    }

    public Map<String, String> select(boolean signedIn, List<Interest> interests, List<ProductDTO> seeds,
            List<ProductDTO> candidates, List<ProductDTO> popular, List<ProductDTO> newest,
            List<Category> categories, Set<String> pickupIds, int limit) {
        Map<String, Category> tree = index(categories);
        Map<String, ProductDTO> seedProducts = byId(seeds);
        Map<String, ProductDTO> pool = byId(candidates);
        popular.forEach(p -> pool.putIfAbsent(p.getProductId(), p));
        if (signedIn) newest.forEach(p -> pool.putIfAbsent(p.getProductId(), p));
        Set<String> popularIds = byId(popular).keySet();
        Set<String> recentPurchases = interests.stream().filter(i -> i.recentlyPurchased)
                .map(i -> i.id).collect(Collectors.toSet());
        List<Ranked> related = new ArrayList<>();
        List<Ranked> backup = new ArrayList<>();
        for (ProductDTO item : pool.values()) {
            if (!ProductView.available(item) && !(ProductView.pickupEligible(item) && pickupIds.contains(item.getProductId()))) continue;
            double score = 0;
            double strongest = 0;
            String reason = "";
            for (Interest interest : interests) {
                ProductDTO seed = seedProducts.get(interest.id);
                if (seed == null || interest.id.equals(item.getProductId())) continue;
                double contribution = interest.score * similarity(seed, item, tree);
                score += contribution;
                if (contribution > strongest) {
                    strongest = contribution;
                    reason = REASONS.get(interest.primary);
                }
            }
            boolean purchased = recentPurchases.contains(item.getProductId());
            Ranked rank = new Ranked(item, (purchased ? .45 : 1) * score + (popularIds.contains(item.getProductId()) ? .08 : 0),
                    reason, purchased, root(tree.get(item.getCategoryId()), tree));
            if (score > 0) related.add(rank);
            backup.add(rank);
        }
        related.sort(Comparator.comparingDouble((Ranked row) -> row.score).reversed()
                .thenComparing(row -> row.item.getProductId()));
        Map<String, Ranked> rankedById = new HashMap<>();
        for (Ranked rank : backup) rankedById.put(rank.item.getProductId(), rank);
        List<Ranked> fallback = new ArrayList<>();
        int position = 0;
        if (!signedIn) {
            for (ProductDTO p : popular) appendFallback(fallback, rankedById.remove(p.getProductId()), "인기 상품이에요");
        } else {
            for (ProductDTO p : popular) {
                if (position % 5 == 4 && position / 5 < newest.size()) {
                    appendFallback(fallback, rankedById.remove(newest.get(position / 5).getProductId()), "새로 나온 상품이에요");
                }
                appendFallback(fallback, rankedById.remove(p.getProductId()), "인기 상품이에요");
                position++;
            }
            for (ProductDTO p : newest) appendFallback(fallback, rankedById.remove(p.getProductId()), "새로 나온 상품이에요");
        }
        fallback.sort(Comparator.comparing(row -> row.purchased));

        Map<String, String> result = new LinkedHashMap<>();
        List<Ranked> recent = new ArrayList<>();
        Set<String> leadingRoots = related.stream().limit(6).map(row -> row.root).collect(Collectors.toSet());
        int exploration = seedProducts.size() <= 2 ? 3 : 6;
        while (result.size() < limit) {
            Ranked next = null;
            if (!related.isEmpty() && (result.size() + 1) % exploration == 0) {
                next = first(fallback, result, recent, leadingRoots, true);
            }
            if (next == null) next = first(related, result, recent, Set.of(), true);
            if (next == null) next = first(fallback, result, recent, Set.of(), signedIn);
            if (next == null) next = first(related, result, recent, Set.of(), false);
            if (next == null) next = first(fallback, result, recent, Set.of(), false);
            if (next == null) break;
            result.put(next.item.getProductId(), next.reason);
            recent.add(next);
            if (recent.size() > 5) recent.remove(0);
        }
        return result;
    }

    private static void appendFallback(List<Ranked> rows, Ranked row, String reason) {
        if (row != null) rows.add(new Ranked(row.item, row.score, reason, row.purchased, row.root));
    }

    private static Ranked first(List<Ranked> rows, Map<String, String> chosen, List<Ranked> recent,
            Set<String> ignoredRoots, boolean diverse) {
        for (Ranked row : rows) {
            if (chosen.containsKey(row.item.getProductId()) || (row.root != null && ignoredRoots.contains(row.root))) continue;
            if (!diverse || diverse(row.item, recent)) return row;
        }
        return null;
    }

    private static boolean diverse(ProductDTO item, List<Ranked> recent) {
        int categoryCount = 0;
        int brandCount = 0;
        String brand = brand(item);
        for (Ranked row : recent) {
            if (Objects.equals(row.item.getCategoryId(), item.getCategoryId())) categoryCount++;
            if (!brand.isEmpty() && brand.equals(brand(row.item))) brandCount++;
        }
        return categoryCount < 3 && brandCount < 3;
    }

    private static double similarity(ProductDTO seed, ProductDTO item, Map<String, Category> tree) {
        Category a = tree.get(seed.getCategoryId());
        Category b = tree.get(item.getCategoryId());
        if (a == null || b == null) return 0;
        double score;
        if (a.id.equals(b.id)) score = 1;
        else if (a.parentId != null && a.parentId.equals(b.parentId) && a.level == 3 && b.level == 3) score = .65;
        else if (Objects.equals(root(a, tree), root(b, tree))) score = .25;
        else return 0;
        String brand = brand(seed);
        if (!brand.isEmpty() && brand.equals(brand(item))) score += .08;
        int high = Math.max(seed.getPrice(), item.getPrice());
        int low = Math.min(seed.getPrice(), item.getPrice());
        if (low > 0 && low / (double) high >= .7) score += .07;
        return score;
    }

    private static String brand(ProductDTO p) {
        return p.getBrand() == null ? "" : p.getBrand().strip().toLowerCase(Locale.ROOT);
    }

    private static String root(Category category, Map<String, Category> tree) {
        if (category == null) return null;
        while (category.parentId != null && tree.containsKey(category.parentId)) category = tree.get(category.parentId);
        return category.id;
    }

    private static Map<String, Category> index(List<Category> categories) {
        return categories.stream().collect(Collectors.toMap(c -> c.id, c -> c));
    }

    private static Map<String, ProductDTO> byId(List<ProductDTO> items) {
        Map<String, ProductDTO> result = new LinkedHashMap<>();
        for (ProductDTO item : items) result.putIfAbsent(item.getProductId(), item);
        return result;
    }

    public static final class Interest {
        public final String id;
        final double score;
        final Kind primary;
        final boolean recentlyPurchased;

        private Interest(String id, double score, Kind primary, boolean recentlyPurchased) {
            this.id = id;
            this.score = score;
            this.primary = primary;
            this.recentlyPurchased = recentlyPurchased;
        }
    }

    public static final class Scope {
        public final List<String> roots;
        public final List<String> leaves;
        public final List<String> parents;

        private Scope(List<String> roots, List<String> leaves, List<String> parents) {
            this.roots = roots;
            this.leaves = leaves;
            this.parents = parents;
        }
    }

    private static final class Ranked {
        final ProductDTO item;
        final double score;
        final String reason;
        final boolean purchased;
        final String root;

        Ranked(ProductDTO item, double score, String reason, boolean purchased, String root) {
            this.item = item;
            this.score = score;
            this.reason = reason;
            this.purchased = purchased;
            this.root = root;
        }
    }
}
