package daiso.member.controller;

import java.sql.Date;

public class MemberProfileInfo {
    private final String loginId;
    private final String email;
    private final String name;
    private final String phone;
    private final Date birthDate;
    private final String gender;

    public MemberProfileInfo(
            String loginId,
            String email,
            String name,
            String phone,
            Date birthDate,
            String gender) {
        this.loginId = loginId;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.gender = gender;
    }

    public String getLoginId() { return loginId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public Date getBirthDate() { return birthDate; }
    public String getGender() { return gender; }
}