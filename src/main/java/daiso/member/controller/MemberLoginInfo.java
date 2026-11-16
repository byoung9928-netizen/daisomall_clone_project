package daiso.member.controller;

public class MemberLoginInfo {
    private String memberId;
    private String email;
    private String passwordHash;
    private String name;
    private String role;
    private String status;

    public MemberLoginInfo(String memberId, String email, String passwordHash,
                           String name, String role, String status) {
        this.memberId = memberId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.status = status;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}