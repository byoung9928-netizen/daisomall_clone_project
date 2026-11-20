package daiso.member.controller;

import java.sql.Timestamp;

public final class AdminMemberInfo {
    private final String memberId;
    private final String loginId;
    private final String name;
    private final String email;
    private final String role;
    private final String status;
    private final Timestamp createdAt;

    public AdminMemberInfo(
            String memberId,
            String loginId,
            String name,
            String email,
            String role,
            String status,
            Timestamp createdAt) {
        this.memberId = memberId;
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}