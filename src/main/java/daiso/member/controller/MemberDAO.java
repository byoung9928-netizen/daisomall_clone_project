package daiso.member.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class MemberDAO {

    private Connection getConnection()
            throws NamingException, SQLException {
        InitialContext context = new InitialContext();
        DataSource dataSource = (DataSource) context.lookup(
                "java:comp/env/jdbc/mysqlDB"
        );
        return dataSource.getConnection();
    }

    public boolean existsByEmail(String email)
            throws NamingException, SQLException {
        String sql = "SELECT 1 FROM members WHERE email = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
    
    public MemberLoginInfo findByEmail(String email)
            throws NamingException, SQLException {
        String sql =
                "SELECT member_id, email, password_hash, name, role, status " +
                "FROM members WHERE email = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                return new MemberLoginInfo(
                        result.getString("member_id"),
                        result.getString("email"),
                        result.getString("password_hash"),
                        result.getString("name"),
                        result.getString("role"),
                        result.getString("status")
                );
            }
        }
    }

    public void insert(String memberId, String email, String passwordHash,
                       String name, String phone)
            throws NamingException, SQLException {
        String sql =
                "INSERT INTO members " +
                "(member_id, email, password_hash, name, phone) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, memberId);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, name);
            statement.setString(5, phone);

            statement.executeUpdate();
        }
    }
}