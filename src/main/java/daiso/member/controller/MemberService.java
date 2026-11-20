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
import java.util.List;
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
    
    public boolean isLoginIdAvailable(String loginId) throws Exception {
        loginId = loginId == null
                ? ""
                : loginId.trim().toLowerCase(Locale.ROOT);

        if (!loginId.matches("^[a-z0-9._-]{4,50}$")) {
            return false;
        }

        return !memberDAO.existsByLoginId(loginId);
    }

    public boolean isEmailAvailable(String email) throws Exception {
        email = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return false;
        }

        return !memberDAO.existsByEmail(email);
    }
    
    public boolean isEmailAvailableForChange(
            String memberId, String email) throws Exception {

        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("로그인 상태를 확인해 주세요.");
        }

        if (email == null) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }

        MemberLoginInfo emailOwner = memberDAO.findByEmail(normalizedEmail);

        // 기존 본인 이메일이면 사용 가능으로 처리
        return emailOwner == null
                || memberId.trim().equals(emailOwner.getMemberId());
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
                || !verifyPassword(password, member.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "아이디(또는 이메일) 또는 비밀번호가 올바르지 않습니다."
            );
        }

        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new IllegalArgumentException(
                    "이용이 중지된 계정입니다. 고객센터에 문의해 주세요."
            );
        }

        return member;
    }
    
    public List<AdminMemberInfo> findAdminMembers(
            String keyword,
            String status) throws Exception {

        String search = keyword == null ? "" : keyword.trim();

        if (search.length() > 120) {
            throw new IllegalArgumentException("검색어는 120자 이내로 입력해 주세요.");
        }

        return memberDAO.findAdminMembers(search, status);
    }
    
    public AdminMemberDetailInfo findAdminMemberDetail(String memberId)
            throws Exception {

        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("회원 정보를 확인해 주세요.");
        }

        return memberDAO.findAdminMemberDetail(memberId.trim());
    }
    
    public AdminMemberConsentInfo findAdminMemberConsent(String memberId)
            throws Exception {

        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("회원 정보를 확인해 주세요.");
        }

        return memberDAO.findAdminMemberConsent(memberId.trim());
    }
    
    public boolean updateAdminMemberStatus(
            String actingAdminId,
            String memberId,
            String status) throws Exception {

        actingAdminId = actingAdminId == null ? "" : actingAdminId.trim();
        memberId = memberId == null ? "" : memberId.trim();
        status = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);

        if (actingAdminId.isEmpty() || memberId.isEmpty()) {
            throw new IllegalArgumentException("회원 정보를 확인해 주세요.");
        }

        if (actingAdminId.equals(memberId)) {
            throw new IllegalArgumentException("관리자 본인 계정의 상태는 변경할 수 없습니다.");
        }

        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
            throw new IllegalArgumentException("변경할 회원 상태를 확인해 주세요.");
        }

        return memberDAO.updateAdminMemberStatus(
                memberId,
                status,
                actingAdminId
        ) == 1;
    }
    
    
    public MemberProfileInfo findProfileByMemberId(String memberId)
            throws Exception {

        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("회원 정보를 확인할 수 없습니다.");
        }

        return memberDAO.findProfileByMemberId(memberId.trim());
    }
    
    
    
    public void updateBasicProfile(
            String memberId,
            String name,
            String phone,
            String birthDateText,
            String gender) throws Exception {

        memberId = memberId == null ? "" : memberId.trim();
        name = name == null ? "" : name.trim();
        phone = phone == null ? "" : phone.replaceAll("\\D", "");
        birthDateText = birthDateText == null ? "" : birthDateText.trim();
        gender = gender == null ? "" : gender.trim();

        if (memberId.isEmpty()) {
            throw new IllegalArgumentException("로그인 정보를 확인해 주세요.");
        }
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("이름을 확인해 주세요.");
        }
        if (!phone.matches("01[016789][0-9]{7,8}")) {
            throw new IllegalArgumentException("휴대폰 번호를 확인해 주세요.");
        }
        if (!gender.isEmpty()
                && !gender.equals("MALE")
                && !gender.equals("FEMALE")) {
            throw new IllegalArgumentException("성별 선택값을 확인해 주세요.");
        }

        java.sql.Date birthDate = null;
        if (!birthDateText.isEmpty()) {
            try {
                LocalDate parsedDate = LocalDate.parse(birthDateText);
                if (parsedDate.isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException("생년월일을 확인해 주세요.");
                }
                birthDate = java.sql.Date.valueOf(parsedDate);
            } catch (java.time.DateTimeException e) {
                throw new IllegalArgumentException("생년월일을 확인해 주세요.");
            }
        }

        boolean updated = memberDAO.updateBasicProfileByMemberId(
                memberId,
                name,
                phone,
                birthDate,
                gender
        );

        if (!updated) {
            throw new IllegalArgumentException("회원정보를 변경하지 못했습니다.");
        }
    }
    
    public java.util.Set<String> findLinkedSocialProviders(String memberId)
            throws Exception {
        return memberDAO.findSocialProvidersByMemberId(memberId);
    }
    
    public void unlinkSocialAccount(String memberId, String provider)
            throws Exception {

        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("로그인 상태를 확인해 주세요.");
        }

        memberId = memberId.trim();

        String normalizedProvider = provider == null
                ? ""
                : provider.trim().toUpperCase(java.util.Locale.ROOT);

        if (!"KAKAO".equals(normalizedProvider)
                && !"NAVER".equals(normalizedProvider)
                && !"GOOGLE".equals(normalizedProvider)) {
            throw new IllegalArgumentException("지원하지 않는 SNS 계정입니다.");
        }

        String loginId = memberDAO.findLoginIdByMemberId(memberId);

        if (loginId == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        java.util.Set<String> linkedProviders =
                memberDAO.findSocialProvidersByMemberId(memberId);

        if (!linkedProviders.contains(normalizedProvider)) {
            throw new IllegalArgumentException("연동된 SNS 계정이 아닙니다.");
        }

        int linkedCount =
                memberDAO.countSocialAccountsByMemberId(memberId);

       

        boolean socialOnlyAccount =
                loginId.startsWith("kakao_")
                || loginId.startsWith("naver_")
                || loginId.startsWith("google_");

        if (socialOnlyAccount && linkedCount <= 1) {
            throw new IllegalArgumentException(
                    "마지막 SNS 계정은 해제할 수 없습니다. 다른 로그인 방법을 먼저 준비해 주세요."
            );
        }

        int deleted = memberDAO.deleteSocialMemberLink(
                normalizedProvider,
                memberId
        );

        if (deleted == 0) {
            throw new IllegalArgumentException("SNS 연동 해제에 실패했습니다.");
        }
    }
    
    public void linkSocialAccount(
            String memberId,
            String provider,
            String providerUserId) throws Exception {

        memberId = memberId == null ? "" : memberId.trim();
        provider = provider == null
                ? ""
                : provider.trim().toUpperCase(java.util.Locale.ROOT);
        providerUserId = providerUserId == null
                ? ""
                : providerUserId.trim();

        if (memberId.isEmpty()) {
            throw new IllegalArgumentException("로그인 상태를 확인해 주세요.");
        }

        if (!"KAKAO".equals(provider)
                && !"NAVER".equals(provider)
                && !"GOOGLE".equals(provider)) {
            throw new IllegalArgumentException("지원하지 않는 SNS 계정입니다.");
        }

        if (providerUserId.isEmpty()) {
            throw new IllegalArgumentException("SNS 회원 정보를 받지 못했습니다.");
        }

        if (memberDAO.findLoginIdByMemberId(memberId) == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        String linkedMemberId =
                memberDAO.findSocialAccountMemberId(provider, providerUserId);

        if (memberId.equals(linkedMemberId)) {
            throw new IllegalArgumentException("이미 현재 계정에 연동되어 있습니다.");
        }

        if (linkedMemberId != null) {
            throw new IllegalArgumentException(
                    "이미 다른 회원 계정에 연동된 SNS입니다."
            );
        }

        memberDAO.insertSocialMemberLink(
                provider,
                providerUserId,
                memberId
        );
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
    
    public MemberLoginInfo loginWithNaver(
            NaverOAuthClient.NaverProfile profile) throws Exception {

        if (profile == null
                || profile.getId() == null
                || profile.getId().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "네이버 회원 번호를 받지 못했습니다."
            );
        }

        String naverId = profile.getId().trim();
        String email = profile.getEmail() == null
                ? ""
                : profile.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = profile.getName() == null
                ? ""
                : profile.getName().trim();

        if (email.isEmpty()
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "네이버 이메일 제공 동의가 필요합니다."
            );
        }

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "네이버에서 이름 정보를 받지 못했습니다."
            );
        }

        MemberLoginInfo naverMember =
                memberDAO.findBySocialAccount("NAVER", naverId);

        if (naverMember != null) {
            return naverMember;
        }

        MemberLoginInfo emailOwner = memberDAO.findByEmail(email);

        if (emailOwner != null) {
            if (!"ACTIVE".equalsIgnoreCase(emailOwner.getStatus())) {
                throw new IllegalArgumentException(
                        "사용할 수 없는 회원 계정의 이메일입니다."
                );
            }

            memberDAO.insertSocialMemberLink(
                    "NAVER",
                    naverId,
                    emailOwner.getMemberId()
            );

            return emailOwner;
        }

        if (name.length() > 100) {
            name = name.substring(0, 100);
        }

        String phone = profile.getMobile() == null
                ? ""
                : profile.getMobile().replaceAll("\\D", "");

        if (!phone.matches("01[016789][0-9]{7,8}")) {
            phone = null;
        }

        String gender = profile.getGender();
        if ("M".equalsIgnoreCase(gender)) {
            gender = "MALE";
        } else if ("F".equalsIgnoreCase(gender)) {
            gender = "FEMALE";
        } else {
            gender = null;
        }

        java.sql.Date birthDate = null;
        String birthYear = profile.getBirthYear();
        String birthday = profile.getBirthday();

        if (birthYear != null
                && birthYear.matches("\\d{4}")
                && birthday != null
                && birthday.matches("\\d{2}-\\d{2}")) {
            try {
                LocalDate date = LocalDate.parse(
                        birthYear + "-" + birthday
                );
                birthDate = java.sql.Date.valueOf(date);
            } catch (RuntimeException ignored) {
                birthDate = null;
            }
        }

        String memberId = UUID.randomUUID().toString();
        String loginId = "naver_" + memberId.replace("-", "");
        String temporaryPassword =
                UUID.randomUUID().toString() + UUID.randomUUID().toString();
        String passwordHash = hashPassword(temporaryPassword);

        return memberDAO.createNaverMember(
                memberId,
                loginId,
                naverId,
                email,
                passwordHash,
                name,
                phone,
                birthDate,
                gender
        );
    }
    
    public String changeEmail(String memberId, String newEmail)
            throws Exception {

        if (memberId == null || memberId.trim().isEmpty()) {
            throw new IllegalArgumentException("로그인 상태를 확인해 주세요.");
        }
        
        memberId = memberId.trim();

        if (newEmail == null) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }

        String email = newEmail.trim().toLowerCase(Locale.ROOT);

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }

        MemberLoginInfo emailOwner = memberDAO.findByEmail(email);

        if (emailOwner != null
                && !memberId.equals(emailOwner.getMemberId())) {
            throw new IllegalArgumentException(
                    "이미 다른 회원이 사용 중인 이메일입니다."
            );
        }

        int updatedRows = memberDAO.updateEmail(memberId, email);

        if (updatedRows != 1) {
            throw new IllegalArgumentException(
                    "이메일을 변경하지 못했습니다. 다시 로그인해 주세요."
            );
        }

        return email;
    }
    
    public MemberLoginInfo loginWithGoogle(
            GoogleOAuthClient.GoogleProfile profile) throws Exception {

        if (profile == null
                || profile.getId() == null
                || profile.getId().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Google 회원 번호를 받지 못했습니다."
            );
        }

        String googleId = profile.getId().trim();

        String email = profile.getEmail() == null
                ? ""
                : profile.getEmail().trim().toLowerCase(Locale.ROOT);

        if (email.isEmpty()
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "Google 이메일 정보를 받지 못했습니다."
            );
        }

        String name = profile.getName() == null
                ? ""
                : profile.getName().trim();

        if (name.isEmpty()) {
            name = email.substring(0, email.indexOf("@"));
        }

        if (name.length() > 100) {
            name = name.substring(0, 100);
        }

        MemberLoginInfo googleMember =
                memberDAO.findBySocialAccount("GOOGLE", googleId);

        if (googleMember != null) {
            return googleMember;
        }

        MemberLoginInfo emailOwner = memberDAO.findByEmail(email);

        if (emailOwner != null) {
            if (!"ACTIVE".equalsIgnoreCase(emailOwner.getStatus())) {
                throw new IllegalArgumentException(
                        "사용할 수 없는 회원 계정의 이메일입니다."
                );
            }

            memberDAO.insertSocialMemberLink(
                    "GOOGLE",
                    googleId,
                    emailOwner.getMemberId()
            );

            return emailOwner;
        }

        String memberId = UUID.randomUUID().toString();
        String loginId = "google_" + memberId.replace("-", "");

        String temporaryPassword =
                UUID.randomUUID().toString() + UUID.randomUUID().toString();

        String passwordHash = hashPassword(temporaryPassword);

        return memberDAO.createGoogleMember(
                memberId,
                loginId,
                googleId,
                email,
                passwordHash,
                name
        );
    }
    
    public String findLoginId(String name, String phone) throws Exception {
        name = name == null ? "" : name.trim();
        phone = phone == null ? "" : phone.trim();

        if (name.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("이름과 전화번호를 입력해 주세요.");
        }

        return memberDAO.findLoginIdByNameAndPhone(name, phone);
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
    
    public void changePassword(
            String memberId,
            String currentPassword,
            String newPassword,
            String confirmPassword) throws Exception {

        memberId = memberId == null ? "" : memberId.trim();

        if (memberId.isEmpty()) {
            throw new IllegalArgumentException("로그인 상태를 확인해 주세요.");
        }

        if (currentPassword == null || currentPassword.isEmpty()
                || newPassword == null || newPassword.isEmpty()
                || confirmPassword == null || confirmPassword.isEmpty()) {
            throw new IllegalArgumentException("모든 비밀번호 항목을 입력해 주세요.");
        }

        if (newPassword.length() < 8 || newPassword.length() > 16
                || !newPassword.matches(
                        "(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*")) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 영문, 숫자, 특수문자를 포함해 8~16자로 입력해 주세요."
            );
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException(
                    "새 비밀번호와 비밀번호 확인이 일치하지 않습니다."
            );
        }

        MemberLoginInfo member = memberDAO.findLoginInfoByMemberId(memberId);

        if (member == null) {
            throw new IllegalArgumentException("회원 정보를 확인할 수 없습니다.");
        }

        if (!verifyPassword(currentPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        if (verifyPassword(newPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요."
            );
        }

        String newPasswordHash = hashPassword(newPassword);

        int updatedRows = memberDAO.updatePasswordByMemberId(
                memberId,
                newPasswordHash
        );

        if (updatedRows != 1) {
            throw new IllegalArgumentException("비밀번호를 변경하지 못했습니다.");
        }

        // 기존 자동 로그인 토큰을 모두 무효화
        memberDAO.deleteRememberTokensByMember(memberId);
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