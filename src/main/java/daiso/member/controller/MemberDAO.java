package daiso.member.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MemberDAO {

    private Connection getConnection()
            throws NamingException, SQLException {
        InitialContext context = new InitialContext();
        DataSource dataSource = (DataSource) context.lookup(
                "java:comp/env/jdbc/mysqlDB"
        );
        return dataSource.getConnection();
    }
    
    private static String adminMemberWhere(
            String keyword, String status, List<String> parameters) {

        String search = keyword == null ? "" : keyword.trim();
        String selectedStatus = status == null || status.trim().isEmpty()
                ? "ALL"
                : status.trim().toUpperCase(Locale.ROOT);

        if (!"ALL".equals(selectedStatus)
                && !"ACTIVE".equals(selectedStatus)
                && !"SUSPENDED".equals(selectedStatus)) {
            throw new IllegalArgumentException("회원 상태 검색값이 올바르지 않습니다.");
        }

        StringBuilder where = new StringBuilder(" FROM members WHERE 1 = 1");

        if (!search.isEmpty()) {
            where.append(" AND (member_id LIKE ? OR login_id LIKE ? "
                    + "OR name LIKE ? OR email LIKE ?)");
            String pattern = "%" + search + "%";
            for (int i = 0; i < 4; i++) {
                parameters.add(pattern);
            }
        }

        if (!"ALL".equals(selectedStatus)) {
            where.append(" AND status = ?");
            parameters.add(selectedStatus);
        }

        return where.toString();
    }

    private static void bindAdminMemberFilters(
            PreparedStatement statement, List<String> parameters)
            throws SQLException {

        for (int i = 0; i < parameters.size(); i++) {
            statement.setString(i + 1, parameters.get(i));
        }
    }
    
    public List<AdminMemberInfo> findAdminMembers(String keyword, String status)
            throws NamingException, SQLException {
        return findAdminMembers(keyword, status, 0, 0);
    }

    List<AdminMemberInfo> findAdminMembers(
            String keyword, String status, int limit, long offset)
            throws NamingException, SQLException {

        List<String> parameters = new ArrayList<>();
        String sql = "SELECT member_id, login_id, name, email, role, status, created_at"
                + adminMemberWhere(keyword, status, parameters)
                + " ORDER BY created_at DESC, member_id ASC";

        if (limit > 0) {
            sql += " LIMIT ? OFFSET ?";
        }

        List<AdminMemberInfo> members = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bindAdminMemberFilters(statement, parameters);

            if (limit > 0) {
                statement.setInt(parameters.size() + 1, limit);
                statement.setLong(parameters.size() + 2, offset);
            }

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    members.add(new AdminMemberInfo(
                            result.getString("member_id"),
                            result.getString("login_id"),
                            result.getString("name"),
                            result.getString("email"),
                            result.getString("role"),
                            result.getString("status"),
                            result.getTimestamp("created_at")
                    ));
                }
            }
        }

        return members;
    }

    long countAdminMembers(String keyword, String status)
            throws NamingException, SQLException {

        List<String> parameters = new ArrayList<>();
        String sql = "SELECT COUNT(*)" + adminMemberWhere(keyword, status, parameters);

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bindAdminMemberFilters(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        }
    }
    
    public AdminMemberDetailInfo findAdminMemberDetail(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT member_id, login_id, name, email, phone, "
                        + "birth_date, gender, role, status, created_at "
                        + "FROM members "
                        + "WHERE member_id = ? "
                        + "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                return new AdminMemberDetailInfo(
                        result.getString("member_id"),
                        result.getString("login_id"),
                        result.getString("name"),
                        result.getString("email"),
                        result.getString("phone"),
                        result.getDate("birth_date"),
                        result.getString("gender"),
                        result.getString("role"),
                        result.getString("status"),
                        result.getTimestamp("created_at")
                );
            }
        }
    }
    
    public AdminMemberConsentInfo findAdminMemberConsent(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT marketing_sms, marketing_email, "
                        + "marketing_personal_info, location_terms, "
                        + "retention_period, updated_at "
                        + "FROM member_consent_settings "
                        + "WHERE member_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                return new AdminMemberConsentInfo(
                        result.getBoolean("marketing_sms"),
                        result.getBoolean("marketing_email"),
                        result.getBoolean("marketing_personal_info"),
                        result.getBoolean("location_terms"),
                        result.getString("retention_period"),
                        result.getTimestamp("updated_at")
                );
            }
        }
    }
    
    
    
    public int saveMemberConsentSettings(
            String memberId,
            boolean marketingSms,
            boolean marketingEmail,
            boolean marketingPersonalInfo,
            boolean locationTerms,
            String retentionPeriod)
            throws NamingException, SQLException {

        String sql =
                "INSERT INTO member_consent_settings "
                        + "(member_id, marketing_sms, marketing_email, "
                        + "marketing_personal_info, location_terms, retention_period) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "marketing_sms = ?, "
                        + "marketing_email = ?, "
                        + "marketing_personal_info = ?, "
                        + "location_terms = ?, "
                        + "retention_period = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);
            statement.setBoolean(2, marketingSms);
            statement.setBoolean(3, marketingEmail);
            statement.setBoolean(4, marketingPersonalInfo);
            statement.setBoolean(5, locationTerms);
            statement.setString(6, retentionPeriod);

            statement.setBoolean(7, marketingSms);
            statement.setBoolean(8, marketingEmail);
            statement.setBoolean(9, marketingPersonalInfo);
            statement.setBoolean(10, locationTerms);
            statement.setString(11, retentionPeriod);

            return statement.executeUpdate();
        }
    }
    
    public int updateAdminMemberStatus(
            String memberId,
            String status,
            String actingAdminId)
            throws NamingException, SQLException {

        String sql =
                "UPDATE members SET status = ? " +
                "WHERE member_id = ? " +
                "AND member_id <> ? " +
                "AND role <> 'ADMIN' " +
                "AND status IN ('ACTIVE', 'SUSPENDED') " +
                "AND status <> ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setString(2, memberId);
            statement.setString(3, actingAdminId);
            statement.setString(4, status);

            return statement.executeUpdate();
        }
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
    
    public String findLoginIdByNameAndPhone(String name, String phone)
            throws NamingException, SQLException {
        String sql =
                "SELECT login_id FROM members " +
                "WHERE name = ? AND phone = ? AND status = 'ACTIVE' " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, phone);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("login_id");
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
    public int updateEmail(String memberId, String email)
            throws NamingException, SQLException {

        String sql =
                "UPDATE members "
                + "SET email = ? "
                + "WHERE member_id = ? AND status = 'ACTIVE'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email);
            statement.setString(2, memberId);

            return statement.executeUpdate();
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
    
    public MemberLoginInfo findLoginInfoByMemberId(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT member_id, email, password_hash, name, role, status " +
                "FROM members " +
                "WHERE member_id = ? AND status = 'ACTIVE' " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

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
    
    public MemberProfileInfo findProfileByMemberId(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT login_id, email, name, phone, birth_date, gender " +
                "FROM members " +
                "WHERE member_id = ? AND status = 'ACTIVE' " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                return new MemberProfileInfo(
                        result.getString("login_id"),
                        result.getString("email"),
                        result.getString("name"),
                        result.getString("phone"),
                        result.getDate("birth_date"),
                        result.getString("gender")
                );
            }
        }
    }
    
    public boolean updateBasicProfileByMemberId(
            String memberId,
            String name,
            String phone,
            java.sql.Date birthDate,
            String gender)
            throws NamingException, SQLException {

        String sql =
                "UPDATE members " +
                "SET name = ?, phone = ?, birth_date = ?, gender = ? " +
                "WHERE member_id = ? AND status = 'ACTIVE'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, phone);
            statement.setDate(3, birthDate);
            statement.setString(4, gender);
            statement.setString(5, memberId);

            return statement.executeUpdate() == 1;
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
    
    public java.util.Set<String> findSocialProvidersByMemberId(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT provider FROM member_social_accounts " +
                "WHERE member_id = ?";

        java.util.Set<String> providers = new java.util.HashSet<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    providers.add(result.getString("provider"));
                }
            }
        }

        return providers;
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
    
    public String findSocialAccountMemberId(
            String provider, String providerUserId)
            throws NamingException, SQLException {

        String sql =
                "SELECT member_id FROM member_social_accounts " +
                "WHERE provider = ? AND provider_user_id = ? " +
                "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, provider);
            statement.setString(2, providerUserId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("member_id") : null;
            }
        }
    }
    
    //이미 가입한 회원과 네이버 계정을 연결할 때 사용
    public void insertSocialMemberLink(
            String provider,
            String providerUserId,
            String memberId)
            throws NamingException, SQLException {

        String sql =
                "INSERT INTO member_social_accounts " +
                "(provider, provider_user_id, member_id) " +
                "VALUES (?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, provider);
            statement.setString(2, providerUserId);
            statement.setString(3, memberId);
            statement.executeUpdate();
        }
    }
    
    public int countSocialAccountsByMemberId(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT COUNT(*) FROM member_social_accounts " +
                "WHERE member_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public String findLoginIdByMemberId(String memberId)
            throws NamingException, SQLException {

        String sql =
                "SELECT login_id FROM members " +
                "WHERE member_id = ? AND status = 'ACTIVE'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memberId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("login_id") : null;
            }
        }
    }

    public int deleteSocialMemberLink(String provider, String memberId)
            throws NamingException, SQLException {

        String sql =
                "DELETE FROM member_social_accounts " +
                "WHERE provider = ? AND member_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, provider);
            statement.setString(2, memberId);

            return statement.executeUpdate();
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
    
    public MemberLoginInfo createNaverMember(
            String memberId,
            String loginId,
            String naverId,
            String email,
            String passwordHash,
            String name,
            String phone,
            java.sql.Date birthDate,
            String gender)
            throws NamingException, SQLException {

        String memberSql =
                "INSERT INTO members " +
                "(member_id, login_id, email, password_hash, name, " +
                "phone, birth_date, gender) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        String socialSql =
                "INSERT INTO member_social_accounts " +
                "(provider, provider_user_id, member_id) " +
                "VALUES ('NAVER', ?, ?)";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(memberSql)) {
                    statement.setString(1, memberId);
                    statement.setString(2, loginId);
                    statement.setString(3, email);
                    statement.setString(4, passwordHash);
                    statement.setString(5, name);
                    statement.setString(6, phone);
                    statement.setDate(7, birthDate);
                    statement.setString(8, gender);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(socialSql)) {
                    statement.setString(1, naverId);
                    statement.setString(2, memberId);
                    statement.executeUpdate();
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
    
    public MemberLoginInfo createGoogleMember(
            String memberId,
            String loginId,
            String googleId,
            String email,
            String passwordHash,
            String name)
            throws NamingException, SQLException {

        String memberSql =
                "INSERT INTO members " +
                "(member_id, login_id, email, password_hash, name, " +
                "phone, birth_date, gender) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        String socialSql =
                "INSERT INTO member_social_accounts " +
                "(provider, provider_user_id, member_id) " +
                "VALUES ('GOOGLE', ?, ?)";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(memberSql)) {
                    statement.setString(1, memberId);
                    statement.setString(2, loginId);
                    statement.setString(3, email);
                    statement.setString(4, passwordHash);
                    statement.setString(5, name);
                    statement.setString(6, null);
                    statement.setDate(7, null);
                    statement.setString(8, null);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(socialSql)) {
                    statement.setString(1, googleId);
                    statement.setString(2, memberId);
                    statement.executeUpdate();
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
    
    public int updatePasswordByMemberId(String memberId, String passwordHash)
            throws NamingException, SQLException {

        String sql =
                "UPDATE members SET password_hash = ? " +
                "WHERE member_id = ? AND status = 'ACTIVE'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, passwordHash);
            statement.setString(2, memberId);

            return statement.executeUpdate();
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
    
    public boolean isWithdrawn(String memberId) throws NamingException, SQLException {
        String sql = "SELECT 1 FROM members WHERE member_id = ? AND status = 'WITHDRAWN'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
    
    public boolean withdrawMember(String memberId, String verifiedPasswordHash)
            throws NamingException, SQLException {
        String updateMemberSql =
                "UPDATE members SET status = 'WITHDRAWN', withdrawn_at = CURRENT_TIMESTAMP "
                + "WHERE member_id = ? AND role = 'MEMBER' AND status = 'ACTIVE' "
                + "AND password_hash = ? "
                + "AND NOT EXISTS ("
                + "    SELECT 1 FROM orders o WHERE o.member_id = ? "
                + "    AND o.status IN ('PENDING', 'PAID', 'PREPARING', "
                + "                     'SHIPPED', 'CANCELLING', 'REFUNDING')"
                + ") "
                + "AND NOT EXISTS ("
                + "    SELECT 1 FROM order_returns r "
                + "    JOIN orders o ON o.order_id = r.order_id "
                + "    WHERE o.member_id = ? "
                + "    AND r.status IN ('REQUESTED', 'APPROVED', 'RECEIVED', 'REFUNDING')"
                + ")";

        String[] cleanupStatements = {
            "DELETE FROM cart_items WHERE member_id = ?",
            "DELETE FROM product_wishlists WHERE member_id = ?",
            "DELETE FROM member_delivery_addresses WHERE member_id = ?",
            "DELETE FROM member_remember_tokens WHERE member_id = ?"
        };

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement statement = connection.prepareStatement(updateMemberSql)) {
                    statement.setString(1, memberId);
                    statement.setString(2, verifiedPasswordHash);
                    statement.setString(3, memberId);
                    statement.setString(4, memberId);

                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }

                for (String sql : cleanupStatements) {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, memberId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                return true;
            } catch (SQLException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
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