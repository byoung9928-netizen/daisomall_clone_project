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

    public enum Scope {
        ALL("전체"), HIDDEN("비노출만"), EMPTY("상품 없음");

        public final String label;

        Scope(String label) { this.label = label; }
    }

    List<TreeItem> tree(String query, Node selected, Scope scope) {
        List<TreeItem> result = new ArrayList<>();
        String keyword = query.toLowerCase(Locale.ROOT);
        for (Node root : roots) append(root, keyword, selected, scope, false, result);
        return result;
    }

    private static boolean append(Node node, String keyword, Node selected, Scope scope,
            boolean ancestorMatch, List<TreeItem> result) {
        int start = result.size();
        boolean directMatch = !keyword.isEmpty() && (node.row.name.toLowerCase(Locale.ROOT).contains(keyword)
                || node.row.id.toLowerCase(Locale.ROOT).contains(keyword));
        boolean keywordMatch = keyword.isEmpty() || ancestorMatch || directMatch;
        boolean ownMatch = keywordMatch && (scope == Scope.ALL || scope == Scope.HIDDEN && !node.effectiveVisible
                || scope == Scope.EMPTY && node.subtreeProducts == 0);
        for (Node child : node.children) append(child, keyword, selected, scope, keywordMatch, result);
        boolean hasResults = result.size() > start;
        boolean selectedPath = selected != null && selected.descendsFrom(node);
        if (!ownMatch && !hasResults && !(selectedPath && scope == Scope.ALL)) return false;
        result.add(start, new TreeItem(node, hasResults, hasResults && (!keyword.isEmpty() || selectedPath || scope != Scope.ALL), directMatch));
        return true;
    }

    String nextId(int highestIssued) {
        int largest = highestIssued;
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

        public boolean hasChildren() { return !children.isEmpty(); }

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

        public final boolean matches;

        private TreeItem(Node node, boolean expandable, boolean open, boolean matches) {
            this.node = node;
            this.expandable = expandable;
            this.open = open;
            this.matches = matches;
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

    public static final class HistoryPage {
        public final String keyword;
        public final String selectedId;
        public final String query;
        public final String operation;
        public final List<Entry> entries;
        public final int page;
        public final String previous;
        public final String next;

        HistoryPage(String context, String keyword, Node selected, String query, String operation,
                List<Entry> entries, int page, int pageSize) {
            this.keyword = keyword;
            selectedId = selected == null ? "" : selected.row.id;
            this.query = query;
            this.operation = operation;
            this.entries = List.copyOf(entries.subList(0, Math.min(entries.size(), pageSize)));
            this.page = page;
            previous = page > 1 ? url(context, page - 1) : null;
            next = entries.size() > pageSize ? url(context, page + 1) : null;
        }

        private String url(String context, int target) {
            return ProductView.url(context, AdminCategoryController.ROUTE, Map.of(
                    "mode", "history", "q", keyword, "edit", selectedId, "historyQuery", query,
                    "operation", operation, "historyPage", Integer.toString(target)));
        }
    }

    public static final class Page {
        public final String keyword;
        public final Scope scope;
        public final Node selected;
        public final List<TreeItem> tree;
        public final List<Node> parents;
        public final boolean historyReady;
        public final boolean deleteReady;
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
        public final String allHistoryUrl;
        public final String resetUrl;
        public final String productsUrl;
        public final String revision;
        private final String context;

        Page(String context, String keyword, Scope scope, AdminCategoryModel model, Node selected, Map<String, String> form,
                boolean create, boolean historyReady, boolean deleteReady, List<Entry> history, Entry latest, int page, int pageSize,
                Impact impact, String confirmToken) {
            this.context = context;
            this.keyword = keyword;
            this.scope = scope;
            this.selected = selected;
            this.create = create;
            this.form = form;
            this.historyReady = historyReady;
            this.deleteReady = deleteReady;
            this.impact = impact;
            this.confirmToken = confirmToken;
            this.latest = latest;
            this.history = List.copyOf(history.subList(0, Math.min(history.size(), pageSize)));
            tree = model.tree(keyword, selected, scope);
            parents = model.parents();
            List<Node> siblings = selected == null ? List.of() : model.siblings(selected.parent);
            int position = selected == null ? -1 : siblings.indexOf(selected);
            canUp = position > 0;
            canDown = position >= 0 && position + 1 < siblings.size();
            String id = selected == null ? "" : selected.row.id;
            revision = selected == null ? "" : selected.version();
            previous = page > 1 && selected != null ? historyUrl(id, page - 1) : null;
            next = history.size() > pageSize ? historyUrl(id, page + 1) : null;
            newUrl = ProductView.url(context, AdminCategoryController.ROUTE, Map.of("q", keyword, "scope", scope.name(), "new", "1"));
            childUrl = selected != null && selected.canParent() ? childUrl(id) : null;
            allHistoryUrl = ProductView.url(context, AdminCategoryController.ROUTE,
                    Map.of("q", keyword, "scope", scope.name(), "edit", id, "mode", "history"));
            resetUrl = editUrl(id);
            productsUrl = selected == null ? null
                    : ProductView.url(context, "/app/admin/products", Map.of("filterCategory", id));
        }

        public String editUrl(String id) {
            return ProductView.url(context, AdminCategoryController.ROUTE, Map.of("q", keyword, "scope", scope.name(), "edit", id));
        }

        public String childUrl(String parentId) {
            return ProductView.url(context, AdminCategoryController.ROUTE, Map.of("q", keyword, "scope", scope.name(), "new", "1", "parent", parentId));
        }

        private String historyUrl(String id, int page) {
            return ProductView.url(context, AdminCategoryController.ROUTE,
                    Map.of("q", keyword, "scope", scope.name(), "edit", id, "historyPage", Integer.toString(page)));
        }
    }
}
