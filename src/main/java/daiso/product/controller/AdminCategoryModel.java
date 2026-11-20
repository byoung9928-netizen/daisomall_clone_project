package daiso.product.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import daiso.product.dao.AdminCategoryDAO.Row;
import daiso.product.dao.AdminCategoryHistoryDAO.Entry;

public final class AdminCategoryModel {
    private static final Comparator<Row> HIERARCHY = Comparator.comparingInt((Row row) -> row.level)
            .thenComparingInt(row -> row.order).thenComparing(row -> row.id);
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Node> roots = new ArrayList<>();

    AdminCategoryModel(List<Row> rows) {
        rows.stream().sorted(HIERARCHY).forEach(row -> {
            Node parent = nodes.get(row.parent);
            Node node = new Node(row, parent);
            nodes.put(row.id, node);
            if (parent == null) roots.add(node);
            else parent.children.add(node);
        });
        roots.forEach(Node::total);
    }

    Node get(String id) { return nodes.get(id); }

    List<Node> siblings(Node parent) { return parent == null ? roots : parent.children; }

    List<Node> parents() {
        return nodes.values().stream().filter(Node::canParent).collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    List<TreeItem> tree(String query, Node selected) {
        List<TreeItem> result = new ArrayList<>();
        String keyword = query.toLowerCase(Locale.ROOT);
        for (Node root : roots) append(root, keyword, selected, false, result);
        return result;
    }

    private static boolean append(Node node, String keyword, Node selected, boolean ancestorMatch, List<TreeItem> result) {
        int start = result.size();
        boolean ownMatch = keyword.isEmpty() || ancestorMatch || node.row.name.toLowerCase(Locale.ROOT).contains(keyword)
                || node.row.id.toLowerCase(Locale.ROOT).contains(keyword);
        for (Node child : node.children) append(child, keyword, selected, ownMatch, result);
        boolean hasResults = result.size() > start;
        boolean selectedPath = selected != null && selected.descendsFrom(node);
        if (!ownMatch && !hasResults && !selectedPath) return false;
        result.add(start, new TreeItem(node, hasResults, hasResults && (!keyword.isEmpty() || selectedPath)));
        return true;
    }

    String nextId() {
        int largest = 0;
        for (String id : nodes.keySet()) {
            if (id.matches("CTGR_[0-9]{5}")) largest = Math.max(largest, Integer.parseInt(id.substring(5)));
        }
        if (largest >= 99999) throw new IllegalArgumentException("신규 카테고리 ID 범위를 확인해 주세요.");
        return String.format(Locale.ROOT, "CTGR_%05d", largest + 1);
    }

    public static final class Node {
        public final Row row;
        public final Node parent;
        public final String path;
        public final boolean effectiveVisible;
        public long subtreeProducts;
        private final List<Node> children = new ArrayList<>();

        private Node(Row row, Node parent) {
            this.row = row;
            this.parent = parent;
            path = parent == null ? row.name : parent.path + " › " + row.name;
            effectiveVisible = row.visible && (parent == null || parent.effectiveVisible);
        }

        private long total() {
            subtreeProducts = row.products + children.stream().mapToLong(Node::total).sum();
            return subtreeProducts;
        }

        boolean descendsFrom(Node ancestor) {
            for (Node node = this; node != null; node = node.parent) {
                if (node == ancestor) return true;
            }
            return false;
        }

        void branch(List<Node> list) {
            list.add(this);
            for (Node child : children) child.branch(list);
        }

        public boolean canParent() { return row.level < 3 && (!row.leaf || row.products == 0); }

        String version() {
            String values = row.order + ":" + (row.visible ? "1" : "0") + (row.leaf ? "1" : "0") + ":" + row.name;
            return java.util.Base64.getEncoder().encodeToString(values.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static final class TreeItem {
        public final Node node;
        public final boolean expandable;
        public final boolean open;

        private TreeItem(Node node, boolean expandable, boolean open) {
            this.node = node;
            this.expandable = expandable;
            this.open = open;
        }
    }

    public static final class Impact {
        public final int descendants;
        public final int products;
        final String fingerprint;

        Impact(int descendants, int products, String fingerprint) {
            this.descendants = descendants;
            this.products = products;
            this.fingerprint = fingerprint;
        }
    }

    public static final class Page {
        public final String keyword;
        public final Node selected;
        public final List<TreeItem> tree;
        public final List<Node> parents;
        public final boolean historyReady;
        public final boolean create;
        public final Map<String, String> form;
        public final boolean canUp;
        public final boolean canDown;
        public final List<Entry> history;
        public final Entry latest;
        public final String previous;
        public final String next;
        public final String confirmToken;
        public final Impact impact;
        public final String newUrl;
        public final String childUrl;
        public final String resetUrl;
        public final String productsUrl;
        public final String revision;
        private final String context;

        Page(String context, String keyword, AdminCategoryModel model, Node selected, Map<String, String> form,
                boolean create, boolean historyReady, List<Entry> history, Entry latest, int page, int pageSize,
                Impact impact, String confirmToken) {
            this.context = context;
            this.keyword = keyword;
            this.selected = selected;
            this.create = create;
            this.form = form;
            this.historyReady = historyReady;
            this.impact = impact;
            this.confirmToken = confirmToken;
            this.latest = latest;
            this.history = List.copyOf(history.subList(0, Math.min(history.size(), pageSize)));
            tree = model.tree(keyword, selected);
            parents = model.parents();
            List<Node> siblings = selected == null ? List.of() : model.siblings(selected.parent);
            int position = selected == null ? -1 : siblings.indexOf(selected);
            canUp = position > 0;
            canDown = position >= 0 && position + 1 < siblings.size();
            String id = selected == null ? "" : selected.row.id;
            revision = selected == null ? "" : selected.version();
            previous = page > 1 && selected != null ? historyUrl(id, page - 1) : null;
            next = history.size() > pageSize ? historyUrl(id, page + 1) : null;
            newUrl = ProductView.url(context, AdminCategoryController.ROUTE, Map.of("q", keyword, "new", "1"));
            childUrl = selected != null && selected.canParent()
                    ? ProductView.url(context, AdminCategoryController.ROUTE, Map.of("q", keyword, "new", "1", "parent", id)) : null;
            resetUrl = editUrl(id);
            productsUrl = selected == null ? null
                    : ProductView.url(context, "/app/admin/products", Map.of("filterCategory", id));
        }

        public String editUrl(String id) {
            return ProductView.url(context, AdminCategoryController.ROUTE, Map.of("q", keyword, "edit", id));
        }

        private String historyUrl(String id, int page) {
            return ProductView.url(context, AdminCategoryController.ROUTE,
                    Map.of("q", keyword, "edit", id, "historyPage", Integer.toString(page)));
        }
    }
}
