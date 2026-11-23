package daiso.order.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Location;

final class PickupStores {
    private PickupStores() {
    }

    static List<Location> findAll() throws SQLException, NamingException {
        DataSource source = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");

        try (Connection connection = source.getConnection()) {
            return new StoreInventoryDAO().locations(connection);
        }
    }
}