package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

final class ProductQueries {
    private static final Set<String> SORTS = Set.of("recommended", "newest", "priceAsc", "priceDesc", "rating", "reviews");
    private static final Set<String> STATUSES = Set.of("", "AVAILABLE", "SOLD_OUT", "SCHEDULED");

    private ProductQueries() {
    }

    static Connection connect() throws SQLException, NamingException {
        DataSource source = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        Connection conn = source.getConnection();
        try {
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            return conn;
        } catch (SQLException error) {
            try {
                conn.close();
            } catch (SQLException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    static ProductDAO.CatalogFilter read(HttpServletRequest request) {
        ProductDAO.CatalogFilter filter = new ProductDAO.CatalogFilter();
        filter.category = text(request, "category", 100);
        filter.keyword = text(request, "q", 100);
        filter.brand = text(request, "brand", 100);
        filter.status = text(request, "status", 20);
        filter.sort = text(request, "sort", 20);
        if (filter.sort.isEmpty()) {
            filter.sort = "recommended";
        }
        if (!SORTS.contains(filter.sort) || !STATUSES.contains(filter.status)) {
            throw new IllegalArgumentException("Unknown sort or status");
        }
        filter.maxPrice = integer(request, "maxPrice", null, 0, Integer.MAX_VALUE);
        filter.limit = integer(request, "limit", 30, 1, 60);
        filter.offset = integer(request, "offset", 0, 0, Integer.MAX_VALUE);
        filter.available = "true".equals(request.getParameter("available"));
        filter.lowStock = "true".equals(request.getParameter("lowStock"));
        filter.topRated = "true".equals(request.getParameter("topRated"));
        return filter;
    }

    static String text(HttpServletRequest request, String name, int maxLength) {
        String value = request.getParameter(name);
        if (value == null) {
            return "";
        }
        value = value.strip();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Parameter is too long");
        }
        return value;
    }

    static Integer integer(HttpServletRequest request, String name, Integer defaultValue, int min, int max) {
        String text = text(request, name, 10);
        if (text.isEmpty()) {
            return defaultValue;
        }
        int value = Integer.parseInt(text);
        if (value < min || value > max) {
            throw new IllegalArgumentException("Number out of range");
        }
        return value;
    }
}
