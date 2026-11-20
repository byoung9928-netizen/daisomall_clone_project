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
                + "(SELECT COUNT(*) FROM products WHERE stock_quantity = 0) AS online_sold_out_count";

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
                    result.getLong("online_sold_out_count")
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

        private Summary(long productCount, long categoryCount,
                        long orderCount, BigDecimal paidAmount,
                        long awaitingShipmentCount, long onlineSoldOutCount) {
            this.productCount = productCount;
            this.categoryCount = categoryCount;
            this.orderCount = orderCount;
            this.paidAmount = paidAmount;
            this.awaitingShipmentCount = awaitingShipmentCount;
            this.onlineSoldOutCount = onlineSoldOutCount;
        }
    }
}