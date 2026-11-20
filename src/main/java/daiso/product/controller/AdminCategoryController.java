package daiso.product.controller;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.AdminCategoryDAO;
import daiso.product.dao.AdminCategoryDAO.Row;

@DaisoController
public final class AdminCategoryController {
    public static final String ROUTE = "/app/admin/categories";
    private static final String MAPPING = "/admin/categories";
    private static final String VIEW = "admin/categories";
    private static final String SAVED = AdminCategoryController.class.getName() + ".saved";
    private final AdminCategoryDAO dao = new AdminCategoryDAO();

    @DaisoGetMapping(MAPPING)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) return blocked(request, response, 403, "관리자 권한이 필요합니다.");
        return show(request, response, session, null);
    }

    @DaisoPostMapping(MAPPING + "/save")
    public String save(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            return blocked(request, response, 403, "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.");
        }

        Map<String, String> draft = new LinkedHashMap<>();
        for (String field : List.of("create", "id", "parent", "name", "order", "visible")) {
            String value = request.getParameter(field);
            draft.put(field, value == null ? "" : value.substring(0, Math.min(value.length(), 150)));
        }
        try {
            boolean create = "1".equals(draft.get("create"));
            String keyword = ProductQueries.text(request, "q", 120);
            String id = ProductQueries.text(request, "id", 100);
            String name = ProductQueries.text(request, "name", 120);
            Integer order = ProductQueries.integer(request, "order", null, 0, Integer.MAX_VALUE);
            String visibleValue = ProductQueries.text(request, "visible", 1);
            if (id.isEmpty() || name.isEmpty() || order == null || !List.of("0", "1").contains(visibleValue)) {
                throw new IllegalArgumentException("카테고리 ID, 이름, 표시 순서와 노출 상태를 확인해 주세요.");
            }
            boolean visible = "1".equals(visibleValue);
            String parent = ProductQueries.text(request, "parent", 100);
            if (create && (!id.matches("[A-Za-z0-9_-]{1,100}") || id.equalsIgnoreCase(parent))) {
                throw new IllegalArgumentException("신규 ID에는 영문, 숫자, 밑줄, 하이픈을 사용할 수 있습니다. 자신의 ID를 부모로 선택할 수 없습니다.");
            }
            try (Connection connection = ProductQueries.connect()) {
                try {
                    if (create) dao.insert(connection, id, parent.isEmpty() ? null : parent, name, order, visible);
                    else dao.update(connection, id, name, order, visible);
                    connection.commit();
                } catch (SQLException | RuntimeException error) {
                    try { connection.rollback(); }
                    catch (SQLException rollback) { error.addSuppressed(rollback); }
                    throw error;
                }
            }
            session.setAttribute(SAVED, true);
            return "redirect:" + ProductView.rawUrl("", MAPPING, Map.of("q", keyword));
        } catch (IllegalArgumentException error) {
            response.setStatus(400);
            request.setAttribute("categoryError", error.getMessage());
        } catch (SQLException | NamingException error) {
            boolean duplicate = error instanceof SQLException && ((SQLException) error).getErrorCode() == 1062;
            if (!duplicate) request.getServletContext().log("관리자 카테고리 저장 실패", error);
            response.setStatus(duplicate ? 409 : 500);
            request.setAttribute("categoryError", duplicate ? "이미 사용 중인 카테고리 ID입니다." : "카테고리를 저장하지 못했습니다.");
        }
        return show(request, response, session, draft);
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session, Map<String, String> draft) {
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 120);
            Map<String, Node> byId = new LinkedHashMap<>();
            for (Row row : dao.list(connection)) {
                Node parent = byId.get(row.parent);
                Node node = new Node(row, parent);
                byId.put(row.id, node);
                if (parent != null) parent.children.add(node);
            }
            List<Group> groups = new ArrayList<>();
            for (Node node : byId.values()) {
                if (node.row.parent != null) continue;
                List<Node> shown = new ArrayList<>();
                collect(node, keyword.toLowerCase(Locale.ROOT), shown);
                if (!shown.isEmpty()) groups.add(new Group(node, shown));
            }
            String edit = draft != null && !"1".equals(draft.get("create")) ? draft.get("id")
                    : ProductQueries.text(request, "edit", 100);
            Node selected = byId.get(edit);
            boolean create = draft != null ? "1".equals(draft.get("create")) : "1".equals(request.getParameter("new"));
            if (draft == null && !edit.isEmpty() && selected == null) {
                response.setStatus(404);
                request.setAttribute("categoryError", "카테고리를 찾을 수 없습니다.");
            }
            Map<String, String> form = draft;
            if (form == null && selected != null) {
                Row row = selected.row;
                form = Map.of("id", row.id, "name", row.name, "order", Integer.toString(row.order),
                        "visible", row.visible ? "1" : "0");
            }
            if (form == null && create) form = Map.of("visible", "1", "order", "0");
            request.setAttribute("categoryPage", new Page(request.getContextPath(), keyword, groups,
                    new ArrayList<>(byId.values()), selected, form, create));
            request.setAttribute("categoryCsrf", AdminProductController.token(session));
            connection.commit();
            synchronized (session) {
                if (session.getAttribute(SAVED) != null) {
                    session.removeAttribute(SAVED);
                    request.setAttribute("categorySaved", true);
                    response.setHeader("Cache-Control", "no-store");
                }
            }
            return VIEW;
        } catch (IllegalArgumentException error) {
            return blocked(request, response, 400, "조회 조건을 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 카테고리 조회 실패", error);
            return blocked(request, response, 500, "카테고리를 조회할 수 없습니다.");
        }
    }

    private static boolean collect(Node node, String keyword, List<Node> shown) {
        int position = shown.size();
        boolean matches = node.path.toLowerCase(Locale.ROOT).contains(keyword)
                || node.row.id.toLowerCase(Locale.ROOT).contains(keyword);
        for (Node child : node.children) {
            if (collect(child, keyword, shown)) matches = true;
            node.subtreeProducts += child.subtreeProducts;
            node.descendants += child.descendants + 1;
        }
        if (matches) shown.add(position, node);
        return matches;
    }

    private static void initialize(HttpServletRequest request) {
        request.setAttribute("categoryReady", true);
        request.setAttribute("adminSection", "categories");
    }

    private static String blocked(HttpServletRequest request, HttpServletResponse response, int status, String error) {
        response.setStatus(status);
        request.setAttribute("categoryError", error);
        return VIEW;
    }

    public static final class Node {
        public final Row row;
        public final String path;
        public final boolean effectiveVisible;
        public long subtreeProducts;
        public int descendants;
        private final List<Node> children = new ArrayList<>();

        private Node(Row row, Node parent) {
            this.row = row;
            path = parent == null ? row.name : parent.path + " › " + row.name;
            effectiveVisible = row.visible && (parent == null || parent.effectiveVisible);
            subtreeProducts = row.products;
        }

        public boolean canParent() { return row.level < 3 && (!row.leaf || row.products == 0); }
    }

    public static final class Group {
        public final Node root;
        public final List<Node> rows;

        private Group(Node root, List<Node> rows) {
            this.root = root;
            this.rows = List.copyOf(rows);
        }
    }

    public static final class Page {
        public final String keyword;
        public final List<Group> groups;
        public final List<Node> parents;
        public final Node selected;
        public final Map<String, String> form;
        public final boolean create;
        public final String resetUrl;
        public final String newUrl;
        private final String context;

        private Page(String context, String keyword, List<Group> groups, List<Node> all, Node selected,
                Map<String, String> form, boolean create) {
            this.context = context;
            this.keyword = keyword;
            this.groups = List.copyOf(groups);
            parents = all.stream().filter(Node::canParent).collect(java.util.stream.Collectors.toUnmodifiableList());
            this.selected = selected;
            this.form = form;
            this.create = create;
            resetUrl = ProductView.url(context, ROUTE, Map.of("q", keyword));
            newUrl = ProductView.url(context, ROUTE, Map.of("q", keyword, "new", "1"));
        }

        public String editUrl(String id) { return ProductView.url(context, ROUTE, Map.of("q", keyword, "edit", id)); }
    }
}
