package daiso.member.controller;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MemberService {

    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberDAO memberDAO = new MemberDAO();

    public void register(
            String loginId,
            String email,
            String password,
            String confirmPassword,
            String name,
            String phone,
            String birthDateText,
            String gender) throws Exception {

        loginId = loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        name = name == null ? "" : name.trim();
        phone = phone == null ? "" : phone.trim();
        birthDateText = birthDateText == null ? "" : birthDateText.trim();
        gender = gender == null ? "" : gender.trim();

        if (loginId.isEmpty() || email.isEmpty() || password == null
                || name.isEmpty() || phone.isEmpty() || birthDateText.isEmpty()) {
            throw new IllegalArgumentException("필수 항목을 모두 입력해 주세요.");
        }

        if (!loginId.matches("^[a-z0-9._-]{4,50}$")) {
            throw new IllegalArgumentException(
                    "아이디는 영문 소문자, 숫자, 점, 밑줄, 하이픈으로 4~50자 입력해 주세요."
            );
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        }

        if (password.length() < 8 || password.length() > 16
                || !password.matches("(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*")) {
            throw new IllegalArgumentException(
                    "비밀번호는 영문, 숫자, 특수문자를 포함해 8~16자로 입력해 주세요."
            );
        }

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException(
                    "비밀번호와 비밀번호 확인이 일치하지 않습니다."
            );
        }

        String phoneDigits = phone.replaceAll("\\D", "");
        if (phoneDigits.length() < 10 || phoneDigits.length() > 11) {
            throw new IllegalArgumentException("휴대폰 번호를 확인해 주세요.");
        }

        LocalDate birthDate;
        try {
            if (!birthDateText.matches("\\d{8}")) {
                throw new IllegalArgumentException(
                        "생년월일을 숫자 8자리로 입력해 주세요. 예: 19970318"
                );
            }

            birthDate = LocalDate.parse(
                    birthDateText,
                    DateTimeFormatter.BASIC_ISO_DATE
            );
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("올바른 생년월일을 입력해 주세요.");
        }

        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일을 확인해 주세요.");
        }

        if (!gender.isEmpty()
                && !gender.equals("MALE")
                && !gender.equals("FEMALE")) {
            throw new IllegalArgumentException("성별 선택값을 확인해 주세요.");
        }

        if (memberDAO.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        if (memberDAO.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String passwordHash = hashPassword(password);
        String memberId = UUID.randomUUID().toString();

        memberDAO.insertWithProfile(
                memberId,
                loginId,
                email,
                passwordHash,
                name,
                phone,
                java.sql.Date.valueOf(birthDate),
                gender
        );
    }
    
    public MemberLoginInfo login(String loginIdOrEmail, String password)
            throws Exception {

        loginIdOrEmail = loginIdOrEmail == null
                ? ""
                : loginIdOrEmail.trim().toLowerCase(Locale.ROOT);

        if (loginIdOrEmail.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "아이디와 비밀번호를 입력해 주세요."
            );
        }

        MemberLoginInfo member =
                memberDAO.findByLoginIdOrEmail(loginIdOrEmail);

        if (member == null
                || !"ACTIVE".equalsIgnoreCase(member.getStatus())
                || !verifyPassword(password, member.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "아이디(또는 이메일) 또는 비밀번호가 올바르지 않습니다."
            );
        }

        return member;
    }
    
    public MemberLoginInfo loginWithKakao(
            String kakaoId, String nickname, String kakaoEmail)
            throws Exception {

        if (kakaoId == null || kakaoId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "카카오 회원 번호를 받지 못했습니다."
            );
        }

        String email = kakaoEmail == null
                ? ""
                : kakaoEmail.trim().toLowerCase(Locale.ROOT);

        if (email.isEmpty()
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "카카오 이메일 제공 동의가 필요합니다. 카카오 동의항목에서 이메일을 허용해 주세요."
            );
        }

        MemberLoginInfo kakaoMember =
                memberDAO.findBySocialAccount("KAKAO", kakaoId);

        MemberLoginInfo emailOwner = memberDAO.findByEmail(email);

        if (kakaoMember != null) {
            if (emailOwner != null
                    && !emailOwner.getMemberId().equals(kakaoMember.getMemberId())) {

                if (!"ACTIVE".equalsIgnoreCase(emailOwner.getStatus())) {
                    throw new IllegalArgumentException(
                            "사용할 수 없는 회원 계정의 이메일입니다."
                    );
                }

                memberDAO.updateKakaoMemberLink(
                        kakaoId,
                        emailOwner.getMemberId()
                );

                return emailOwner;
            }

            if (!email.equalsIgnoreCase(kakaoMember.getEmail())) {
                memberDAO.updateEmailByMemberId(
                        kakaoMember.getMemberId(),
                        email
                );
            }

            return new MemberLoginInfo(
                    kakaoMember.getMemberId(),
                    email,
                    kakaoMember.getPasswordHash(),
                    kakaoMember.getName(),
                    kakaoMember.getRole(),
                    kakaoMember.getStatus()
            );
        }

        if (emailOwner != null) {
            if (!"ACTIVE".equalsIgnoreCase(emailOwner.getStatus())) {
                throw new IllegalArgumentException(
                        "사용할 수 없는 회원 계정의 이메일입니다."
                );
            }

            memberDAO.insertKakaoMemberLink(
                    kakaoId,
                    emailOwner.getMemberId()
            );

            return emailOwner;
        }


        String name = nickname == null || nickname.trim().isEmpty()
                ? "카카오 회원"
                : nickname.trim();

        if (name.length() > 100) {
            name = name.substring(0, 100);
        }

        String memberId = UUID.randomUUID().toString();
        String temporaryPassword =
                UUID.randomUUID().toString() + UUID.randomUUID().toString();
        String passwordHash = hashPassword(temporaryPassword);

        return memberDAO.createKakaoMember(
                memberId,
                kakaoId,
                email,
                passwordHash,
                name
        );
    }
    
    public String findEmail(String name, String phone) throws Exception {
        name = name == null ? "" : name.trim();
        phone = phone == null ? "" : phone.trim();

        if (name.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("이름과 전화번호를 입력해 주세요.");
        }

        String email = memberDAO.findEmailByNameAndPhone(name, phone);

        if (email == null) {
            return null;
        }

        return email;
    }
    
    public String createRememberToken(String memberId) throws Exception {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);

        // 브라우저에 전달할 원본 토큰
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);

        // DB에는 원본 대신 SHA-256 해시를 저장
        String tokenHash = hashRememberToken(rawToken);

        Timestamp expiresAt = Timestamp.valueOf(
                LocalDateTime.now().plusDays(30)
        );

        memberDAO.insertRememberToken(tokenHash, memberId, expiresAt);

        return rawToken;
    }

    public MemberLoginInfo findMemberByRememberToken(String rawToken)
            throws Exception {
        if (rawToken == null || rawToken.isEmpty()) {
            return null;
        }

        String tokenHash = hashRememberToken(rawToken);
        return memberDAO.findMemberByRememberToken(tokenHash);
    }

    public void deleteRememberToken(String rawToken) throws Exception {
        if (rawToken == null || rawToken.isEmpty()) {
            return;
        }

        String tokenHash = hashRememberToken(rawToken);
        memberDAO.deleteRememberToken(tokenHash);
    }

    private String hashRememberToken(String rawToken)
            throws GeneralSecurityException {
        byte[] hashBytes = MessageDigest
                .getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();

        for (byte value : hashBytes) {
            hex.append(String.format("%02x", value & 0xff));
        }

        return hex.toString();
    }
    
    public void resetPassword(
            String email, String name, String phone, String newPassword)
            throws Exception {

        email = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
        name = name == null ? "" : name.trim();
        phone = phone == null ? "" : phone.trim();

        if (email.isEmpty() || name.isEmpty()
                || phone.isEmpty() || newPassword == null
                || newPassword.isEmpty()) {
            throw new IllegalArgumentException("모든 항목을 입력해 주세요.");
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        }

        if (newPassword.length() < 4) {
            throw new IllegalArgumentException("새 비밀번호는 4자 이상 입력해 주세요.");
        }

        String passwordHash = hashPassword(newPassword);

        boolean updated = memberDAO.updatePasswordByVerifiedInfo(
                email, name, phone, passwordHash
        );

        if (!updated) {
            throw new IllegalArgumentException(
                    "입력한 회원 정보와 일치하는 계정을 찾을 수 없습니다."
            );
        }
    }

    private boolean verifyPassword(String password, String storedPasswordHash)
            throws GeneralSecurityException {
        if (storedPasswordHash == null) {
            return false;
        }

        String[] parts = storedPasswordHash.split("\\$", -1);

        if (parts.length != 3) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);

            if (iterations <= 0 || expectedHash.length == 0) {
                return false;
            }

            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    iterations,
                    expectedHash.length * 8
            );

            try {
                byte[] actualHash = SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();

                return MessageDigest.isEqual(expectedHash, actualHash);
            } finally {
                spec.clearPassword();
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String hashPassword(String password)
            throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        byte[] hash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();

        spec.clearPassword();

        return ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }
}