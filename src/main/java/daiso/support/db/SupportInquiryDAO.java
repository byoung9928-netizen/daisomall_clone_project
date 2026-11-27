package daiso.support.db;

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
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Location;

public class SupportInquiryDAO {

    private Connection getConnection() throws NamingException, SQLException {
        DataSource dataSource = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        return dataSource.getConnection();
    }
    
    public List<Location> findStores() throws NamingException, SQLException {
        try (Connection connection = getConnection()) {
            return new StoreInventoryDAO().locations(connection);
        }
    }

    public void insert(String memberId, String title, String content,
            String channel, String category, String subcategory,
            String storeId, String productId, byte[] photo)
            throws NamingException, SQLException {
    	
    	String sql = "INSERT INTO support_inquiries "
    	        + "(member_id, title, content, channel, category, subcategory, store_id, product_id, photo_data) "
    	        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId);
            statement.setString(2, title);
            statement.setString(3, content);
            statement.setString(4, channel);
            statement.setString(5, category);
            statement.setString(6, subcategory);
            statement.setString(7, storeId);
            statement.setString(8, productId);
            statement.setBytes(9, photo);
            statement.executeUpdate();
        }
    }

    public List<Map<String, Object>> findByMemberId(String memberId)
            throws NamingException, SQLException {

        String sql = "SELECT inquiry_id, title, content, status, answer, created_at, answered_at, photo_data IS NOT NULL AS has_photo "
                + "FROM support_inquiries "
                + "WHERE member_id = ? "
                + "ORDER BY created_at DESC, inquiry_id DESC";

        List<Map<String, Object>> inquiries = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, Object> inquiry = new LinkedHashMap<>();
                    inquiry.put("inquiryId", rows.getLong("inquiry_id"));
                    inquiry.put("title", rows.getString("title"));
                    inquiry.put("content", rows.getString("content"));
                    inquiry.put("status", rows.getString("status"));
                    inquiry.put("answer", rows.getString("answer"));
                    inquiry.put("createdAt", rows.getTimestamp("created_at"));
                    inquiry.put("answeredAt", rows.getTimestamp("answered_at"));
                    inquiry.put("hasPhoto", rows.getBoolean("has_photo"));
                    inquiries.add(inquiry);
                }
            }
        }

        return inquiries;
    }
    
    public long countPending() throws NamingException, SQLException {
        String sql = "SELECT COUNT(*) FROM support_inquiries WHERE status = 'PENDING'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }
    
    public List<Map<String, Object>> findForAdmin() throws NamingException, SQLException {
    	String sql = "SELECT i.inquiry_id, m.login_id, i.title, i.content, "
    	        + "i.status, i.answer, i.created_at, "
    	        + "i.channel, i.category, i.subcategory, "
    	        + "s.store_name, p.name AS product_name, i.photo_data IS NOT NULL AS has_photo "
    	        + "FROM support_inquiries i "
    	        + "JOIN members m ON m.member_id = i.member_id "
    	        + "LEFT JOIN store_locations s ON s.store_id = i.store_id "
    	        + "LEFT JOIN products p ON p.product_id = i.product_id "
    	        + "ORDER BY (i.status = 'PENDING') DESC, i.created_at DESC, i.inquiry_id DESC "
    	        + "LIMIT 50";

        List<Map<String, Object>> inquiries = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {

            while (rows.next()) {
                Map<String, Object> inquiry = new LinkedHashMap<>();
                inquiry.put("inquiryId", rows.getLong("inquiry_id"));
                inquiry.put("loginId", rows.getString("login_id"));
                inquiry.put("title", rows.getString("title"));
                inquiry.put("content", rows.getString("content"));
                inquiry.put("status", rows.getString("status"));
                inquiry.put("answer", rows.getString("answer"));
                inquiry.put("createdAt", rows.getTimestamp("created_at"));
                inquiry.put("channel", rows.getString("channel"));
                inquiry.put("category", rows.getString("category"));
                inquiry.put("subcategory", rows.getString("subcategory"));
                inquiry.put("storeName", rows.getString("store_name"));
                inquiry.put("productName", rows.getString("product_name"));
                inquiry.put("hasPhoto", rows.getBoolean("has_photo"));
                inquiries.add(inquiry);
            }
        }

        return inquiries;
    }

    public boolean saveAnswer(long inquiryId, String answer)
            throws NamingException, SQLException {
        String sql = "UPDATE support_inquiries "
                + "SET answer = ?, status = 'ANSWERED', answered_at = CURRENT_TIMESTAMP "
                + "WHERE inquiry_id = ? AND status = 'PENDING'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, answer);
            statement.setLong(2, inquiryId);
            return statement.executeUpdate() == 1;
        }
    }
    public byte[] findPhoto(long inquiryId, String viewerId)
            throws NamingException, SQLException {
        String sql = "SELECT i.photo_data FROM support_inquiries i "
                + "JOIN members m ON m.member_id = ? AND m.status = 'ACTIVE' "
                + "WHERE i.inquiry_id = ? "
                + "AND (i.member_id = m.member_id OR m.role = 'ADMIN')";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, viewerId);
            statement.setLong(2, inquiryId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getBytes("photo_data") : null;
            }
        }
    }
}