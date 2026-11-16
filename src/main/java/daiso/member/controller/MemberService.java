package daiso.member.controller;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
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

    public void register(String email, String password,
                        String name, String phone) throws Exception {
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        name = name == null ? "" : name.trim();
        phone = phone == null ? "" : phone.trim();

        if (email.isEmpty() || password == null || password.isEmpty()
                || name.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("모든 항목을 입력해 주세요.");
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        }

        if (password.length() < 4) {
            throw new IllegalArgumentException("비밀번호는 4자 이상 입력해 주세요.");
        }

        if (memberDAO.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String passwordHash = hashPassword(password);
        String memberId = UUID.randomUUID().toString();

        memberDAO.insert(memberId, email, passwordHash, name, phone);
    }
    
    public MemberLoginInfo login(String email, String password)
            throws Exception {
        email = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);

        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "이메일과 비밀번호를 입력해 주세요."
            );
        }

        MemberLoginInfo member = memberDAO.findByEmail(email);

        if (member == null
                || !"ACTIVE".equalsIgnoreCase(member.getStatus())
                || !verifyPassword(password, member.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        return member;
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