package daiso.member.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Timestamp;

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
    
    public boolean existsByLoginId(String loginId)
            throws NamingException, SQLException {
        String sql = "SELECT 1 FROM members WHERE login_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, loginId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
    
    public String findEmailByNameAndPhone(String name, String phone)
            throws NamingException, SQLException {
        String sql =
                "SELECT email FROM members " +
                "WHERE name = ? AND phone = ? AND status = 'ACTIVE' " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, phone);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("email");
                }
                return null;
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
    
    public MemberLoginInfo findByLoginIdOrEmail(String value)
            throws NamingException, SQLException {

        String sql =
                "SELECT member_id, email, password_hash, name, role, status " +
                "FROM members " +
                "WHERE login_id = ? OR email = ? " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, value);
            statement.setString(2, value);

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
    
    public void updateEmailByMemberId(String memberId, String email)
            throws NamingException, SQLException {

        String sql =
                "UPDATE members SET email = ? " +
                "WHERE member_id = ? AND status = 'ACTIVE'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email);
            statement.setString(2, memberId);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("회원 이메일을 변경하지 못했습니다.");
            }
        }
    }
    
    public MemberLoginInfo findBySocialAccount(
            String provider, String providerUserId)
            throws NamingException, SQLException {

        String sql =
                "SELECT m.member_id, m.email, m.password_hash, " +
                "m.name, m.role, m.status " +
                "FROM members m " +
                "JOIN member_social_accounts s " +
                "ON m.member_id = s.member_id " +
                "WHERE s.provider = ? " +
                "AND s.provider_user_id = ? " +
                "AND m.status = 'ACTIVE' " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, provider);
            statement.setString(2, providerUserId);

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
    
    public void updateKakaoMemberLink(String kakaoId, String memberId)
            throws NamingException, SQLException {

        String sql =
                "UPDATE member_social_accounts SET member_id = ? " +
                "WHERE provider = 'KAKAO' AND provider_user_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);
            statement.setString(2, kakaoId);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("기존 카카오 연결을 변경하지 못했습니다.");
            }
        }
    }

    public void insertKakaoMemberLink(String kakaoId, String memberId)
            throws NamingException, SQLException {

        String sql =
                "INSERT INTO member_social_accounts " +
                "(provider, provider_user_id, member_id) " +
                "VALUES ('KAKAO', ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, kakaoId);
            statement.setString(2, memberId);
            statement.executeUpdate();
        }
    }

    public MemberLoginInfo createKakaoMember(
            String memberId,
            String kakaoId,
            String email,
            String passwordHash,
            String name)
            throws NamingException, SQLException {

        String kakaoLoginId =
                "kakao_" + memberId.replace("-", "");

        String memberSql =
                "INSERT INTO members " +
                "(member_id, login_id, email, password_hash, name, phone) " +
                "VALUES (?, ?, ?, ?, ?, NULL)";

        String socialSql =
                "INSERT INTO member_social_accounts " +
                "(provider, provider_user_id, member_id) " +
                "VALUES ('KAKAO', ?, ?)";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement memberStatement =
                             connection.prepareStatement(memberSql)) {
                    memberStatement.setString(1, memberId);
                    memberStatement.setString(2, kakaoLoginId);
                    memberStatement.setString(3, email);
                    memberStatement.setString(4, passwordHash);
                    memberStatement.setString(5, name);
                    memberStatement.executeUpdate();
                }

                try (PreparedStatement socialStatement =
                             connection.prepareStatement(socialSql)) {
                    socialStatement.setString(1, kakaoId);
                    socialStatement.setString(2, memberId);
                    socialStatement.executeUpdate();
                }

                connection.commit();

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }

        return new MemberLoginInfo(
                memberId,
                email,
                passwordHash,
                name,
                "MEMBER",
                "ACTIVE"
        );
    }
    public boolean updatePasswordByVerifiedInfo(
            String email, String name, String phone, String passwordHash)
            throws NamingException, SQLException {

        String sql =
                "UPDATE members SET password_hash = ? " +
                "WHERE email = ? AND name = ? AND phone = ? " +
                "AND status = 'ACTIVE'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, passwordHash);
            statement.setString(2, email);
            statement.setString(3, name);
            statement.setString(4, phone);

            return statement.executeUpdate() > 0;
        }
    }
    
    public void insertRememberToken(
            String tokenHash, String memberId, Timestamp expiresAt)
            throws NamingException, SQLException {

        String sql =
                "INSERT INTO member_remember_tokens " +
                "(token_hash, member_id, expires_at) VALUES (?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tokenHash);
            statement.setString(2, memberId);
            statement.setTimestamp(3, expiresAt);

            statement.executeUpdate();
        }
    }

    public MemberLoginInfo findMemberByRememberToken(String tokenHash)
            throws NamingException, SQLException {

        String sql =
                "SELECT m.member_id, m.email, m.password_hash, " +
                "m.name, m.role, m.status " +
                "FROM members m " +
                "JOIN member_remember_tokens t " +
                "ON m.member_id = t.member_id " +
                "WHERE t.token_hash = ? " +
                "AND t.expires_at > CURRENT_TIMESTAMP " +
                "AND m.status = 'ACTIVE' " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tokenHash);

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

    public void deleteRememberToken(String tokenHash)
            throws NamingException, SQLException {

        String sql =
                "DELETE FROM member_remember_tokens WHERE token_hash = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tokenHash);
            statement.executeUpdate();
        }
    }

    public void deleteRememberTokensByMember(String memberId)
            throws NamingException, SQLException {

        String sql =
                "DELETE FROM member_remember_tokens WHERE member_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);
            statement.executeUpdate();
        }
    }
    
    public void insertWithProfile(
            String memberId,
            String loginId,
            String email,
            String passwordHash,
            String name,
            String phone,
            java.sql.Date birthDate,
            String gender)
            throws NamingException, SQLException {

        String sql =
                "INSERT INTO members " +
                "(member_id, login_id, email, password_hash, name, " +
                "phone, birth_date, gender) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);
            statement.setString(2, loginId);
            statement.setString(3, email);
            statement.setString(4, passwordHash);
            statement.setString(5, name);
            statement.setString(6, phone);
            statement.setDate(7, birthDate);

            if (gender == null || gender.isEmpty()) {
                statement.setNull(8, java.sql.Types.VARCHAR);
            } else {
                statement.setString(8, gender);
            }

            statement.executeUpdate();
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