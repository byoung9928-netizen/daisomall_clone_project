package daiso.order.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class DeliveryAddressDAO {
    public List<Map<String, String>> findByMemberId(String memberId)
            throws SQLException, NamingException {

        String sql = "SELECT address_id, address_name, recipient, phone, "
                + "postcode, address1, address2 "
                + "FROM member_delivery_addresses "
                + "WHERE member_id = ? "
                + "ORDER BY created_at DESC, address_id ASC";

        DataSource dataSource =
                InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        List<Map<String, String>> addresses = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, String> address = new LinkedHashMap<>();
                    address.put("id", rows.getString("address_id"));
                    address.put("addressName", rows.getString("address_name"));
                    address.put("recipient", rows.getString("recipient"));
                    address.put("phone", rows.getString("phone"));
                    address.put("postcode", rows.getString("postcode"));
                    address.put("address1", rows.getString("address1"));
                    address.put("address2", rows.getString("address2"));
                    addresses.add(address);
                }
            }
        }

        return addresses;
    }
    public void insert(String memberId, String addressId,
            Map<String, String> address)
 throws SQLException, NamingException {

String sql = "INSERT INTO member_delivery_addresses "
     + "(address_name, recipient, phone, postcode, address1, address2, "
     + "member_id, address_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

writeAddress(sql, memberId, addressId, address);
}

public int update(String memberId, String addressId,
           Map<String, String> address)
 throws SQLException, NamingException {

String sql = "UPDATE member_delivery_addresses "
     + "SET address_name = ?, recipient = ?, phone = ?, "
     + "postcode = ?, address1 = ?, address2 = ? "
     + "WHERE member_id = ? AND address_id = ?";

return writeAddress(sql, memberId, addressId, address);
}

private int writeAddress(String sql, String memberId, String addressId,
                  Map<String, String> address)
 throws SQLException, NamingException {

DataSource dataSource =
     InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");

try (Connection connection = dataSource.getConnection();
  PreparedStatement statement = connection.prepareStatement(sql)) {

 statement.setString(1, address.get("addressName"));
 statement.setString(2, address.get("recipient"));
 statement.setString(3, address.get("phone"));
 statement.setString(4, address.get("postcode"));
 statement.setString(5, address.get("address1"));
 statement.setString(6, address.get("address2"));
 statement.setString(7, memberId);
 statement.setString(8, addressId);

 return statement.executeUpdate();
}
}
    
}