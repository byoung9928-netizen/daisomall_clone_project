package daiso.product.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.naming.NamingException;

import daiso.product.dao.ProductConnectionFactory;
import daiso.product.dao.WishlistDAO;
import daiso.product.dto.WishlistPage;

public final class WishlistService {
    private static final int PAGE_SIZE = 30;
    private final WishlistDAO dao = new WishlistDAO();

    // 입력값 검증: 회원은 컨트롤러가 세션에서 확인한 값만 전달
    private static String identifier(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.strip())
                || value.codePointCount(0, value.length()) > 100
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + "을 확인해 주세요.");
        }
        return value;
    }

    // 기존 상품 조회 트랜잭션을 재사용하는 일괄 상태 조회
    public Set<String> findSelected(Connection connection, String memberId, Collection<String> productIds)
            throws SQLException {
        identifier(memberId, "회원 정보");
        Set<String> ids = new LinkedHashSet<>(productIds);
        if (ids.size() > 100) throw new IllegalArgumentException("조회 상품 수를 확인해 주세요.");
        for (String id : ids) identifier(id, "상품 정보");
        return dao.findSelected(connection, memberId, ids);
    }

    // 목록과 총건수는 같은 조회 트랜잭션에서 확인
    public WishlistPage findPage(String memberId, int requestedPage) throws SQLException, NamingException {
        identifier(memberId, "회원 정보");
        if (requestedPage < 1 || requestedPage > 1000000) {
            throw new IllegalArgumentException("페이지 번호를 확인해 주세요.");
        }
        try (Connection connection = ProductConnectionFactory.openQueryConnection()) {
            int total = dao.count(connection, memberId);
            int pages = (int) Math.max(1L, (total + (long) PAGE_SIZE - 1) / PAGE_SIZE);
            int page = Math.min(requestedPage, pages);
            WishlistPage result = new WishlistPage(
                    dao.findPage(connection, memberId, PAGE_SIZE, (page - 1) * PAGE_SIZE), total, page, pages);
            connection.commit();
            return result;
        }
    }

    // 등록 및 해제: 커밋이 성공한 뒤에만 호출자에게 성공 반환
    public void setSelected(String memberId, String productId, boolean selected)
            throws SQLException, NamingException {
        identifier(memberId, "회원 정보");
        identifier(productId, "상품 정보");
        try (Connection connection = ProductConnectionFactory.openWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                if (selected) {
                    if (!dao.lockProduct(connection, productId)) {
                        throw new IllegalArgumentException("존재하지 않는 상품입니다.");
                    }
                    dao.add(connection, memberId, productId);
                } else {
                    dao.remove(connection, memberId, productId);
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); }
                catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        }
    }

    // 테이블 미적용은 다른 DB 오류와 구분하여 명시적으로 안내
    public static boolean isStorageMissing(SQLException error) {
        for (SQLException current = error; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 1146 && current.getMessage() != null
                    && current.getMessage().contains("product_wishlists")) return true;
        }
        return false;
    }
}
