package daiso.member.controller;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class AdminDashboardDAO {
	
	private static final String ONLINE_SOLD_OUT_SQL =
	        "SELECT COUNT(*) FROM products WHERE stock_quantity = 0";

	private static final String PENDING_RETURNS_SQL =
	        "SELECT COUNT(*) FROM order_returns WHERE status = 'REQUESTED'";

	public long countOnlineSoldOut() throws NamingException, SQLException {
	    return count(ONLINE_SOLD_OUT_SQL);
	}

	public long countPendingReturns() throws NamingException, SQLException {
	    return count(PENDING_RETURNS_SQL);
	}
	
	public long countAwaitingShipment() throws NamingException, SQLException {
	    return findSummary().awaitingShipmentCount;
	}

	private long count(String sql) throws NamingException, SQLException {
	    DataSource dataSource = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");

	    try (Connection connection = dataSource.getConnection();
	         PreparedStatement statement = connection.prepareStatement(sql);
	         ResultSet result = statement.executeQuery()) {
	        if (!result.next()) {
	            throw new SQLException("관리자 집계 결과가 없습니다.");
	        }
	        return result.getLong(1);
	    }
	}

    public Summary findSummary() throws NamingException, SQLException {
        String sql =
                "SELECT "
                + "(SELECT COUNT(*) FROM products) AS product_count, "
                + "(SELECT COUNT(*) FROM categories) AS category_count, "
                + "(SELECT COUNT(*) FROM orders) AS order_count, "
                + "(SELECT COALESCE(SUM(o.total_amount), 0) "
                + " FROM orders o "
                + " JOIN payment_attempts p ON p.payment_id = ("
                + "     SELECT MAX(p2.payment_id) "
                + "     FROM payment_attempts p2 WHERE p2.order_id = o.order_id"
                + " ) "
                + " WHERE p.status = 'APPROVED' "
                + " AND o.status IN ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED') "
                + " AND o.canceled_at IS NULL) AS paid_amount, "
                + "(SELECT COUNT(*) FROM orders o "
                + " JOIN payment_attempts p ON p.payment_id = ("
                + "     SELECT MAX(p2.payment_id) "
                + "     FROM payment_attempts p2 WHERE p2.order_id = o.order_id"
                + " ) "
                + " WHERE p.status = 'APPROVED' "
                + " AND o.status IN ('PAID', 'PREPARING') "
                + " AND o.canceled_at IS NULL) AS awaiting_shipment_count, "
                + "(" + ONLINE_SOLD_OUT_SQL + ") AS online_sold_out_count, "
                + "(" + PENDING_RETURNS_SQL + ") AS pending_return_count";
        

        DataSource dataSource = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("대시보드 집계 결과가 없습니다.");
            }

            return new Summary(
                    result.getLong("product_count"),
                    result.getLong("category_count"),
                    result.getLong("order_count"),
                    result.getBigDecimal("paid_amount"),
                    result.getLong("awaiting_shipment_count"),
                    result.getLong("online_sold_out_count"),
                    result.getLong("pending_return_count")
            );
        }
    }

    public static final class Summary {
        public final long productCount;
        public final long categoryCount;
        public final long orderCount;
        public final BigDecimal paidAmount;
        public final long awaitingShipmentCount;
        public final long onlineSoldOutCount;
        public final long pendingReturnCount;

        private Summary(long productCount, long categoryCount,
                        long orderCount, BigDecimal paidAmount,
                        long awaitingShipmentCount, long onlineSoldOutCount, long pendingReturnCount) {
            this.productCount = productCount;
            this.categoryCount = categoryCount;
            this.orderCount = orderCount;
            this.paidAmount = paidAmount;
            this.awaitingShipmentCount = awaitingShipmentCount;
            this.onlineSoldOutCount = onlineSoldOutCount;
            this.pendingReturnCount = pendingReturnCount;
        }
    }
}