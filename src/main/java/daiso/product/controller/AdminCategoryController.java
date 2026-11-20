package daiso.product.controller;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.controller.AdminCategoryModel.Impact;
import daiso.product.controller.AdminCategoryModel.Node;
import daiso.product.controller.AdminCategoryModel.Page;
import daiso.product.dao.AdminCategoryDAO;
import daiso.product.dao.AdminCategoryHistoryDAO;
import daiso.product.dao.AdminCategoryHistoryDAO.Action;
import daiso.product.dao.AdminCategoryHistoryDAO.Entry;

@DaisoController
public final class AdminCategoryController {
    public static final String ROUTE = "/app/admin/categories";
    private static final String MAPPING = "/admin/categories";
    private static final String VIEW = "admin/categories";
    private static final String SAVED = AdminCategoryController.class.getName() + ".saved";
    private static final String CONFIRM = AdminCategoryController.class.getName() + ".confirm";
    private static final String DELETE_CONFIRM = AdminCategoryController.class.getName() + ".delete";
    private static final int HISTORY_SIZE = 10;
    private final AdminCategoryDAO dao = new AdminCategoryDAO();
    private final AdminCategoryHistoryDAO history = new AdminCategoryHistoryDAO();

    @DaisoGetMapping(MAPPING)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) return blocked(request, response, 403, "관리자 권한이 필요합니다.");
        return show(request, response, session, null, null);
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
        for (String field : List.of("id", "parent", "name", "visible", "create")) {
            String value = request.getParameter(field);
            draft.put(field, value == null ? "" : value.substring(0, Math.min(value.length(), 150)));
        }
        String intent = request.getParameter("intent");
        boolean move = "up".equals(intent) || "down".equals(intent);
        Pending preview = null;
        try {
            boolean create = "1".equals(draft.get("create"));
            String keyword = ProductQueries.text(request, "q", 120);
            String id = ProductQueries.text(request, "id", 100);
            String actor = session.getAttribute("member_id").toString();
            if (intent == null || !List.of("save", "confirm", "up", "down").contains(intent)
                    || create && !"save".equals(intent)) {
                throw new IllegalArgumentException("실행할 작업을 확인해 주세요.");
            }
            try (Connection connection = ProductQueries.connect()) {
                try {
                    if (!history.available(connection)) throw new IllegalStateException("카테고리 이력 SQL 적용 후 변경할 수 있습니다.");
                    AdminCategoryModel model = new AdminCategoryModel(dao.lockTree(connection));
                    if (move) {
                        Node selected = requiredNode(model, id);
                        List<Node> siblings = new ArrayList<>(model.siblings(selected.parent));
                        int position = siblings.indexOf(selected);
                        int target = position + ("up".equals(intent) ? -1 : 1);
                        if (target < 0 || target >= siblings.size()) {
                            throw new IllegalArgumentException("현재 위치에서 더 이동할 수 없습니다.");
                        }
                        Collections.swap(siblings, position, target);
                        renumber(connection, siblings, actor);
                    } else {
                        String name = ProductQueries.text(request, "name", 120);
                        String visibility = ProductQueries.text(request, "visible", 1);
                        if (name.isEmpty() || !List.of("0", "1").contains(visibility)) {
                            throw new IllegalArgumentException("카테고리 이름과 노출 상태를 확인해 주세요.");
                        }
                        boolean visible = "1".equals(visibility);
                        if (create) {
                            String parentId = ProductQueries.text(request, "parent", 100);
                            Node parent = parentId.isEmpty() ? null : requiredNode(model, parentId);
                            List<Node> siblings = model.siblings(parent);
                            id = model.nextId(history.highestIssuedId(connection));
                            dao.insert(connection, id, parent == null ? null : parent.row, name, siblings.size() + 1, visible);
                            renumber(connection, siblings, actor);
                            history.record(connection, id, name, actor, Action.CREATE, null, name + " · " + visibility(visible));
                        } else {
                            Node selected = requiredNode(model, id);
                            if (!selected.version().equals(request.getParameter("revision"))) {
                                throw new IllegalStateException("다른 작업에서 카테고리가 바뀌었습니다. 현재 정보를 확인한 뒤 다시 저장해 주세요.");
                            }
                            if (selected.row.visible && !visible) {
                                Impact impact = impact(connection, selected);
                                Object current = session.getAttribute(CONFIRM);
                                if (!"confirm".equals(intent) || !(current instanceof Pending)
                                        || !((Pending) current).matches(request.getParameter("confirmation"), id, name, impact)) {
                                    if ("confirm".equals(intent)) {
                                        response.setStatus(409);
                                        request.setAttribute("categoryError", "카테고리나 영향 범위가 바뀌었습니다. 다시 확인해 주세요.");
                                    }
                                    preview = new Pending(id, name, impact);
                                }
                            }
                            if (preview == null) {
                                if ("confirm".equals(intent) && (visible || !selected.row.visible)) {
                                    throw new IllegalStateException("현재 노출 상태가 바뀌었습니다. 다시 확인해 주세요.");
                                }
                                update(connection, selected, name, visible, actor);
                            }
                        }
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException error) {
                    try { connection.rollback(); }
                    catch (SQLException rollback) { error.addSuppressed(rollback); }
                    throw error;
                }
            }
            if (preview != null) {
                session.setAttribute(CONFIRM, preview);
                return show(request, response, session, draft, preview);
            }
            if ("confirm".equals(intent)) session.removeAttribute(CONFIRM);
            String message = create ? "카테고리가 등록되었습니다."
                    : move ? "카테고리 순서가 변경되었습니다." : "카테고리 정보가 수정되었습니다.";
            return savedRedirect(session, keyword, id, message);
        } catch (IllegalArgumentException error) {
            response.setStatus(400);
            request.setAttribute("categoryError", error.getMessage());
        } catch (IllegalStateException error) {
            response.setStatus(409);
            request.setAttribute("categoryError", error.getMessage());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 카테고리 저장 실패", error);
            response.setStatus(500);
            request.setAttribute("categoryError", "변경을 저장하지 못했습니다. 현재 상태를 다시 확인해 주세요.");
        }
        return show(request, response, session, move ? null : draft, null);
    }

    @DaisoPostMapping(MAPPING + "/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            return blocked(request, response, 403, "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.");
        }
        try {
            String keyword = ProductQueries.text(request, "q", 120);
            String id = ProductQueries.text(request, "id", 100);
            String intent = request.getParameter("intent");
            boolean confirm = "confirm".equals(intent);
            if (!confirm && !"preview".equals(intent)) throw new IllegalArgumentException("삭제 확인 단계를 다시 진행해 주세요.");
            String parentId;
            try (Connection connection = ProductQueries.connect()) {
                try {
                    if (!history.supportsDeletion(connection)) {
                        throw new IllegalStateException("삭제 이력 마이그레이션을 적용한 뒤 삭제할 수 있습니다.");
                    }
                    Node selected = requiredNode(new AdminCategoryModel(dao.lockTree(connection)), id);
                    if (selected.hasChildren() || dao.hasProducts(connection, id)) {
                        throw new IllegalStateException("하위 카테고리나 연결 상품이 있어 삭제할 수 없습니다. 숨기려면 비노출로 설정해 주세요.");
                    }
                    parentId = selected.row.parent == null ? "" : selected.row.parent;
                    if (confirm) {
                        Object value = session.getAttribute(DELETE_CONFIRM);
                        if (!(value instanceof PendingDelete) || !((PendingDelete) value).matches(
                                request.getParameter("confirmation"), selected)) {
                            throw new IllegalStateException("카테고리 정보가 변경됐거나 확인이 만료됐습니다. 삭제 내용을 다시 확인해 주세요.");
                        }
                        dao.delete(connection, selected.row);
                        history.record(connection, selected.row, session.getAttribute("member_id").toString(), Action.DELETE,
                                selected.row.name + " · " + visibility(selected.row.visible), "삭제");
                    } else {
                        PendingDelete pending = new PendingDelete(selected);
                        session.setAttribute(DELETE_CONFIRM, pending);
                        request.setAttribute("categoryDeleteToken", pending.token);
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException error) {
                    try { connection.rollback(); }
                    catch (SQLException rollback) { error.addSuppressed(rollback); }
                    throw error;
                }
            }
            if (confirm) {
                session.removeAttribute(DELETE_CONFIRM);
                return savedRedirect(session, keyword, parentId, "카테고리가 삭제되었습니다.");
            }
        } catch (IllegalArgumentException error) {
            response.setStatus(400);
            request.setAttribute("categoryError", error.getMessage());
        } catch (IllegalStateException error) {
            response.setStatus(409);
            request.setAttribute("categoryError", error.getMessage());
        } catch (SQLException | NamingException error) {
            boolean linked = error instanceof SQLException && ((SQLException) error).getErrorCode() == 1451;
            if (!linked) request.getServletContext().log("관리자 카테고리 삭제 실패", error);
            response.setStatus(linked ? 409 : 500);
            request.setAttribute("categoryError", linked ? "연결된 데이터가 있어 삭제할 수 없습니다. 비노출 설정을 이용해 주세요."
                    : "카테고리를 삭제하지 못했습니다. 다시 시도해 주세요.");
        }
        return show(request, response, session, null, null);
    }

    private static String savedRedirect(HttpSession session, String keyword, String id, String message) {
        String receipt = UUID.randomUUID().toString();
        session.setAttribute(SAVED, new SavedNotice(receipt, message));
        return "redirect:" + ProductView.rawUrl("", MAPPING, Map.of("q", keyword, "edit", id, "saved", receipt));
    }

    private void renumber(Connection connection, List<Node> siblings, String actor) throws SQLException {
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int index = 0; index < siblings.size(); index++) {
            Node node = siblings.get(index);
            int order = index + 1;
            if (node.row.order == order) continue;
            positions.put(node.row.id, order);
            history.record(connection, node.row, actor, Action.ORDER, Integer.toString(node.row.order), Integer.toString(order));
        }
        dao.updateOrders(connection, positions);
    }

    private void update(Connection connection, Node node, String name, boolean visible, String actor) throws SQLException {
        boolean nameChanged = !node.row.name.equals(name);
        boolean visibilityChanged = node.row.visible != visible;
        if (!nameChanged && !visibilityChanged) throw new IllegalArgumentException("변경된 카테고리 정보가 없습니다.");
        dao.update(connection, node.row, name, visible);
        if (nameChanged) history.record(connection, node.row.id, name, actor, Action.NAME, node.row.name, name);
        if (visibilityChanged) history.record(connection, node.row.id, name, actor, Action.VISIBILITY,
                visibility(node.row.visible), visibility(visible));
    }

    private Impact impact(Connection connection, Node selected) throws SQLException {
        List<Node> branch = new ArrayList<>();
        selected.branch(branch);
        List<String> affected = new ArrayList<>();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("영향 범위를 계산할 수 없습니다.", error);
        }
        for (Node node = selected.parent; node != null; node = node.parent) {
            addDigest(digest, node.row.id);
            addDigest(digest, node.version());
        }
        for (Node node : branch) {
            addDigest(digest, node.row.id);
            addDigest(digest, node.version());
            if (node.effectiveVisible) affected.add(node.row.id);
        }
        List<String> products = dao.affectedProducts(connection, affected);
        for (String product : products) addDigest(digest, product);
        return new Impact(Math.max(0, affected.size() - 1), products.size(),
                Base64.getEncoder().encodeToString(digest.digest()));
    }

    private static void addDigest(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Map<String, String> draft, Pending preview) {
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 120);
            String id = draft != null ? draft.get("id") : ProductQueries.text(request, "edit", 100);
            boolean create = draft != null ? "1".equals(draft.get("create")) : "1".equals(request.getParameter("new"));
            String parent = create ? (draft != null ? draft.get("parent") : ProductQueries.text(request, "parent", 100)) : "";
            if (id.isEmpty() && !create) id = ProductQueries.text(request, "id", 100);
            AdminCategoryModel model = new AdminCategoryModel(dao.list(connection));
            Node selected = model.get(create ? parent : id);
            if (!create && !id.isEmpty() && selected == null && draft == null) {
                response.setStatus(404);
                request.setAttribute("categoryError", "카테고리를 찾을 수 없습니다.");
            }
            Map<String, String> form = draft;
            if (form == null && create) form = Map.of("parent", parent, "visible", "1");
            if (form == null && selected != null) {
                form = Map.of("name", selected.row.name, "visible", selected.row.visible ? "1" : "0");
            }
            boolean ready = history.available(connection);
            boolean deleteReady = ready && history.supportsDeletion(connection);
            int page = ProductQueries.integer(request, "historyPage", 1, 1, 1_000_000);
            List<Entry> entries = ready && selected != null
                    ? history.list(connection, selected.row.id, HISTORY_SIZE + 1, (page - 1) * HISTORY_SIZE) : List.of();
            Entry latest = null;
            if (ready && selected != null) {
                List<Entry> first = page == 1 ? entries : history.list(connection, selected.row.id, 1, 0);
                if (!first.isEmpty()) latest = first.get(0);
            }
            request.setAttribute("categoryPage", new Page(request.getContextPath(), keyword, model, selected, form,
                    create, ready, deleteReady, entries, latest, page, HISTORY_SIZE, preview == null ? null : preview.impact,
                    preview == null ? "" : preview.token));
            request.setAttribute("categoryCsrf", AdminProductController.token(session));
            response.setHeader("Cache-Control", "no-store");
            connection.commit();
            synchronized (session) {
                String receipt = request.getParameter("saved");
                Object value = session.getAttribute(SAVED);
                if (value instanceof SavedNotice && ((SavedNotice) value).receipt.equals(receipt)) {
                    session.removeAttribute(SAVED);
                    request.setAttribute("categorySaved", ((SavedNotice) value).message);
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

    private static Node requiredNode(AdminCategoryModel model, String id) {
        Node node = model.get(id);
        if (node == null) throw new IllegalArgumentException("선택한 카테고리를 찾을 수 없습니다.");
        return node;
    }

    private static String visibility(boolean visible) { return visible ? "노출" : "비노출"; }

    private static void initialize(HttpServletRequest request) {
        request.setAttribute("categoryReady", true);
        request.setAttribute("adminSection", "categories");
    }

    private static String blocked(HttpServletRequest request, HttpServletResponse response, int status, String error) {
        response.setStatus(status);
        request.setAttribute("categoryError", error);
        return VIEW;
    }

    private static final class SavedNotice {
        private final String receipt;
        private final String message;

        private SavedNotice(String receipt, String message) {
            this.receipt = receipt;
            this.message = message;
        }
    }

    private static final class PendingDelete {
        private final String token = UUID.randomUUID().toString();
        private final String id;
        private final String revision;

        private PendingDelete(Node selected) {
            id = selected.row.id;
            revision = selected.version();
        }

        boolean matches(String token, Node selected) {
            return this.token.equals(token) && id.equals(selected.row.id) && revision.equals(selected.version());
        }
    }

    private static final class Pending {
        private final String token = UUID.randomUUID().toString();
        private final String id;
        private final String name;
        private final Impact impact;

        private Pending(String id, String name, Impact impact) {
            this.id = id;
            this.name = name;
            this.impact = impact;
        }

        boolean matches(String token, String id, String name, Impact impact) {
            return this.token.equals(token) && this.id.equals(id) && this.name.equals(name)
                    && this.impact.fingerprint.equals(impact.fingerprint);
        }
    }
}
