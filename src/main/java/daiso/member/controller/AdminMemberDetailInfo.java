package daiso.member.controller;

import java.sql.Date;
import java.sql.Timestamp;

public final class AdminMemberDetailInfo {
    private final String memberId;
    private final String loginId;
    private final String name;
    private final String email;
    private final String phone;
    private final Date birthDate;
    private final String gender;
    private final String role;
    private final String status;
    private final Timestamp createdAt;

    public AdminMemberDetailInfo(
            String memberId,
            String loginId,
            String name,
            String email,
            String phone,
            Date birthDate,
            String gender,
            String role,
            String status,
            Timestamp createdAt) {
        this.memberId = memberId;
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.gender = gender;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getMemberId() { return memberId; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Date getBirthDate() { return birthDate; }
    public String getGender() { return gender; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public Timestamp getCreatedAt() { return createdAt; }
}