package daiso.product.controller;

import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.Stock;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Applies relative quantity changes within the caller's store-authorized transaction. */
final class StoreManagerQuantityChange {
    private StoreManagerQuantityChange() {}

    static void apply(Connection connection, StoreInventoryDAO dao, String store, Change change,
            Map<String, Integer> amounts, String reason, String memberId) throws SQLException {
        if (change != Change.RECEIVE && change != Change.DAMAGE) {
            throw new IllegalArgumentException("수량을 증감하는 작업만 저장할 수 있습니다.");
        }

        Map<Stock, Integer> results = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> item : new TreeMap<>(amounts).entrySet()) {
            Stock before = dao.find(connection, store, item.getKey(), true);
            if (before == null || !before.carried || !before.id.equals(item.getKey())) {
                throw new IllegalArgumentException("취급 중인 소속 매장 상품만 재고를 변경할 수 있습니다.");
            }
            if (item.getValue() <= 0) throw new IllegalArgumentException("변경 수량은 1개 이상이어야 합니다.");
            results.put(before, change.after(before.quantity, item.getValue()));
        }

        for (Map.Entry<Stock, Integer> result : results.entrySet()) {
            dao.update(connection, store, result.getKey(), result.getValue(), change, reason, memberId);
        }
    }
}
