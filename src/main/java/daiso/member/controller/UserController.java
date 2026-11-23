package daiso.member.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import daiso.common.annotation.DaisoResponseBody;
import java.util.UUID;

@DaisoController
public class UserController {
	
	private static final String REMEMBER_COOKIE = "DAISO_REMEMBER";
	private static final int REMEMBER_COOKIE_AGE = 30 * 24 * 60 * 60;
    private final MemberService memberService = new MemberService();

    @DaisoGetMapping("/member/signup")
    public String signupForm() {
        return "member/signup";
    }

    @DaisoPostMapping("/member/signup")
    public String signup(HttpServletRequest request) {
        try {
            request.setCharacterEncoding("UTF-8");

            String loginId = request.getParameter("loginId");
            String email = request.getParameter("email");
            String password = request.getParameter("password");
            String confirmPassword = request.getParameter("confirmPassword");
            String name = request.getParameter("name");
            String phone = request.getParameter("phone");
            String birthDate = request.getParameter("birthDate");
            String gender = request.getParameter("gender");

            memberService.register(
                    loginId,
                    email,
                    password,
                    confirmPassword,
                    name,
                    phone,
                    birthDate,
                    gender
            );

            return "redirect:/member/login?signup=success";

        } catch (IllegalArgumentException e) {
        	keepSignupInput(request);
            request.setAttribute("signupError", e.getMessage());
            return "member/signup";

        } catch (Exception e) {
            e.printStackTrace();
            keepSignupInput(request);
            request.setAttribute("signupError", "가입 처리 중 오류가 발생했습니다.");
            return "member/signup";
        }
        
    }
    
    @DaisoGetMapping("/member/signup/check-login-id")
    @DaisoResponseBody
    public String checkSignupLoginId(
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");

        String loginId = request.getParameter("loginId");

        if (loginId == null
                || !loginId.trim().toLowerCase(java.util.Locale.ROOT)
                        .matches("^[a-z0-9._-]{4,50}$")) {
            return "invalid";
        }

        try {
            return memberService.isLoginIdAvailable(loginId)
                    ? "available"
                    : "duplicate";
        } catch (Exception e) {
            request.getServletContext().log("아이디 중복 확인 실패", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "error";
        }
    }

    @DaisoGetMapping("/member/signup/check-email")
    @DaisoResponseBody
    public String checkSignupEmail(
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");

        String email = request.getParameter("email");

        if (email == null
                || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "invalid";
        }

        try {
            return memberService.isEmailAvailable(email)
                    ? "available"
                    : "duplicate";
        } catch (Exception e) {
            request.getServletContext().log("이메일 중복 확인 실패", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "error";
        }
    }
    
    private void keepSignupInput(HttpServletRequest request) {
        String[] fields = {
            "loginId", "email", "name", "phone", "birthDate", "gender"
        };

        for (String field : fields) {
            request.setAttribute(field, request.getParameter(field));
        }
    }

    @DaisoGetMapping("/member/login")
    public String loginForm(HttpServletRequest request) {
        if ("success".equals(request.getParameter("passwordReset"))) {
            request.setAttribute(
                    "loginNotice",
                    "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요."
            );
        }
        String naverStatus = request.getParameter("naver");

        if ("cancelled".equals(naverStatus)) {
            request.setAttribute("loginError", "네이버 로그인을 취소했습니다.");
        } else if ("stateError".equals(naverStatus)) {
            request.setAttribute("loginError", "네이버 로그인 확인에 실패했습니다. 다시 시도해 주세요.");
        } else if ("failed".equals(naverStatus)) {
            request.setAttribute("loginError", "네이버 로그인 처리 중 오류가 발생했습니다.");
        }

        String state = UUID.randomUUID().toString();
        request.getSession().setAttribute("naverOAuthState", state);
        request.setAttribute("naverOAuthState", state);

        try {
            request.setAttribute(
                    "naverClientId",
                    new NaverOAuthClient().getClientId()
            );
        } catch (Exception e) {
            request.getServletContext().log("네이버 Client ID 설정을 읽지 못했습니다.", e);
        }

        return "member/login";
    }
    

    @DaisoPostMapping("/member/login")
    public String login(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            request.setCharacterEncoding("UTF-8");

            String loginIdOrEmail = request.getParameter("loginId");
            String password = request.getParameter("password");

            MemberLoginInfo member =
                    memberService.login(loginIdOrEmail, password);

            HttpSession session = request.getSession();
            request.changeSessionId();

            session.setAttribute("member_id", member.getMemberId());
            session.setAttribute("email", member.getEmail());
            session.setAttribute("name", member.getName());
            session.setAttribute("role", member.getRole());

            // 이 브라우저에 기존 자동 로그인 토큰이 있으면 먼저 삭제합니다.
            String oldToken = findCookieValue(request, REMEMBER_COOKIE);
            if (oldToken != null) {
                memberService.deleteRememberToken(oldToken);
            }

            if ("on".equals(request.getParameter("autoLogin"))) {
                String token =
                        memberService.createRememberToken(member.getMemberId());

                addRememberCookie(request, response, token);
            } else {
                expireRememberCookie(request, response);
            }

            if ("ADMIN".equalsIgnoreCase(member.getRole())) {
                return "redirect:/admin/dashboard";
            }
            return "redirect:/home";

        } catch (IllegalArgumentException e) {
            request.setAttribute("loginError", e.getMessage());
            return "member/login";

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute(
                    "loginError",
                    "로그인 처리 중 오류가 발생했습니다."
            );
            return "member/login";
        }
    }
    
    @DaisoGetMapping("/member/oauth/kakao/start")
    public String kakaoStart(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);

            if (session == null || session.getAttribute("member_id") == null) {
                return "redirect:/member/login";
            }

            String memberId = String.valueOf(session.getAttribute("member_id"));
            String state = UUID.randomUUID().toString();

            session.setAttribute("kakaoOAuthLinkMemberId", memberId);
            session.setAttribute("kakaoOAuthState", state);

            String redirectUri =
                    "http://localhost:8080/daisomall/app/member/oauth/kakao/callback";

            String authUrl =
                    "https://kauth.kakao.com/oauth/authorize"
                    + "?client_id=" + java.net.URLEncoder.encode(
                            new KakaoOAuthClient().getRestApiKey(), "UTF-8")
                    + "&redirect_uri=" + java.net.URLEncoder.encode(
                            redirectUri, "UTF-8")
                    + "&response_type=code"
                    + "&state=" + java.net.URLEncoder.encode(state, "UTF-8");

            return "redirect:" + authUrl;

        } catch (Exception e) {
            request.getServletContext().log("카카오 계정 연동 시작 실패", e);
            return "redirect:/order/mypage?section=profile&social=failed";
        }
    }
    
    @DaisoGetMapping("/member/oauth/kakao/callback")
    public String kakaoCallback(
            HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession oauthSession = request.getSession(false);

        String linkingMemberId = oauthSession == null
                ? null
                : (String) oauthSession.getAttribute("kakaoOAuthLinkMemberId");

        String savedState = oauthSession == null
                ? null
                : (String) oauthSession.getAttribute("kakaoOAuthState");

        boolean linkMode = linkingMemberId != null;

        if (linkMode && oauthSession != null) {
            oauthSession.removeAttribute("kakaoOAuthLinkMemberId");
            oauthSession.removeAttribute("kakaoOAuthState");
        }

        String state = request.getParameter("state");

        if (linkMode
                && (savedState == null || state == null || !savedState.equals(state))) {
            return "redirect:/order/mypage?section=profile&social=stateError";
        }

        if (request.getParameter("error") != null) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=cancelled"
                    : "redirect:/member/login";
        }

        String code = request.getParameter("code");

        if (code == null || code.trim().isEmpty()) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=failed"
                    : "redirect:/member/login";
        }

        try {
            KakaoOAuthClient.KakaoProfile profile =
                    new KakaoOAuthClient().getProfile(code);

            if (linkMode) {
                HttpSession currentSession = request.getSession(false);
                String currentMemberId = currentSession == null
                        ? null
                        : (String) currentSession.getAttribute("member_id");

                if (currentMemberId == null
                        || !linkingMemberId.equals(currentMemberId)) {
                    return "redirect:/member/login";
                }

                memberService.linkSocialAccount(
                        currentMemberId,
                        "KAKAO",
                        profile.getId()
                );

                return "redirect:/order/mypage?section=profile&social=linked";
            }

            MemberLoginInfo member = memberService.loginWithKakao(
                    profile.getId(),
                    profile.getNickname(),
                    profile.getEmail()
            );

            String oldToken = findCookieValue(request, REMEMBER_COOKIE);

            if (oldToken != null) {
                try {
                    memberService.deleteRememberToken(oldToken);
                } catch (Exception e) {
                    request.getServletContext().log(
                            "기존 자동 로그인 토큰 삭제 실패",
                            e
                    );
                }
            }

            expireRememberCookie(request, response);

            HttpSession memberSession = request.getSession();
            request.changeSessionId();

            memberSession.setAttribute("member_id", member.getMemberId());
            memberSession.setAttribute("email", member.getEmail());
            memberSession.setAttribute("name", member.getName());
            memberSession.setAttribute("role", member.getRole());

            return "redirect:/home";

        } catch (IllegalArgumentException e) {
            if (linkMode) {
                return "redirect:/order/mypage?section=profile&social=failed";
            }

            request.setAttribute("loginError", e.getMessage());
            return "member/login";

        } catch (Exception e) {
            request.getServletContext().log("카카오 로그인/연동 처리 실패", e);

            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=failed"
                    : "redirect:/member/login";
        }
    }
    
    @DaisoGetMapping("/member/oauth/naver/start")
    public String naverStart(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(true);
            boolean linkMode = "link".equals(request.getParameter("mode"));

            if (linkMode) {
                Object memberId = session.getAttribute("member_id");

                if (memberId == null) {
                    return "redirect:/member/login";
                }

                session.setAttribute(
                        "naverOAuthLinkMemberId",
                        String.valueOf(memberId)
                );
            } else {
                session.removeAttribute("naverOAuthLinkMemberId");
            }

            String state = UUID.randomUUID().toString();
            session.setAttribute("naverOAuthState", state);

            String redirectUri =
                    "http://localhost:8080/daisomall/app/member/oauth/naver/callback";

            String authUrl =
                    "https://nid.naver.com/oauth2.0/authorize"
                    + "?response_type=code"
                    + "&client_id=" + java.net.URLEncoder.encode(
                            new NaverOAuthClient().getClientId(), "UTF-8")
                    + "&redirect_uri=" + java.net.URLEncoder.encode(
                            redirectUri, "UTF-8")
                    + "&state=" + java.net.URLEncoder.encode(state, "UTF-8");

            return "redirect:" + authUrl;

        } catch (Exception e) {
            request.getServletContext().log("네이버 로그인/연동 시작 실패", e);

            if ("link".equals(request.getParameter("mode"))) {
                return "redirect:/order/mypage?section=profile&social=failed";
            }

            return "redirect:/member/login?naver=failed";
        }
    }
    
    @DaisoGetMapping("/member/oauth/naver/callback")
    public String naverCallback(
            HttpServletRequest request,
            HttpServletResponse response) {

        String state = request.getParameter("state");
        HttpSession oauthSession = request.getSession(false);

        String savedState = oauthSession == null
                ? null
                : (String) oauthSession.getAttribute("naverOAuthState");
       
        String linkingMemberId = oauthSession == null
                ? null
                : (String) oauthSession.getAttribute("naverOAuthLinkMemberId");

        if (oauthSession != null) {
            oauthSession.removeAttribute("naverOAuthState");
            oauthSession.removeAttribute("naverOAuthLinkMemberId");
        }
        boolean linkMode = linkingMemberId != null;

        if (savedState == null || state == null || !savedState.equals(state)) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=stateError"
                    : "redirect:/member/login?naver=stateError";
        }

        if (request.getParameter("error") != null) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=cancelled"
                    : "redirect:/member/login?naver=cancelled";
        }

        String code = request.getParameter("code");

        if (code == null || code.trim().isEmpty()) {
        	return linkMode
        	        ? "redirect:/order/mypage?section=profile&social=failed"
        	        : "redirect:/member/login?naver=failed";
        }

        try {
            NaverOAuthClient.NaverProfile profile =
                    new NaverOAuthClient().getProfile(code, state);

            if (linkMode) {
                HttpSession currentSession = request.getSession(false);
                String currentMemberId = currentSession == null
                        ? null
                        : (String) currentSession.getAttribute("member_id");

                if (currentMemberId == null
                        || !linkingMemberId.equals(currentMemberId)) {
                    return "redirect:/member/login";
                }

                memberService.linkSocialAccount(
                        currentMemberId,
                        "NAVER",
                        profile.getId()
                );

                return "redirect:/order/mypage?section=profile&social=linked";
            }

            MemberLoginInfo member =
                    memberService.loginWithNaver(profile);

            String oldToken = findCookieValue(request, REMEMBER_COOKIE);

            if (oldToken != null) {
                try {
                    memberService.deleteRememberToken(oldToken);
                } catch (Exception e) {
                    request.getServletContext().log(
                            "기존 자동 로그인 토큰 삭제 실패",
                            e
                    );
                }
            }

            expireRememberCookie(request, response);

            HttpSession memberSession = request.getSession();
            request.changeSessionId();

            memberSession.setAttribute("member_id", member.getMemberId());
            memberSession.setAttribute("email", member.getEmail());
            memberSession.setAttribute("name", member.getName());
            memberSession.setAttribute("role", member.getRole());

            return "redirect:/home";

        } catch (Exception e) {
            request.getServletContext().log("네이버 로그인 처리 실패", e);
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=failed"
                    : "redirect:/member/login?naver=failed";
        }
    }
    
    @DaisoGetMapping("/member/oauth/google/callback")
    public String googleCallback(
            HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession oauthSession = request.getSession(false);

        String state = request.getParameter("state");
        String savedState = oauthSession == null
                ? null
                : (String) oauthSession.getAttribute("googleOAuthState");

        String linkingMemberId = oauthSession == null
                ? null
                : (String) oauthSession.getAttribute("googleOAuthLinkMemberId");

        if (oauthSession != null) {
            oauthSession.removeAttribute("googleOAuthState");
            oauthSession.removeAttribute("googleOAuthLinkMemberId");
        }

        boolean linkMode = linkingMemberId != null;

        if (savedState == null || state == null || !savedState.equals(state)) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=stateError"
                    : "redirect:/member/login?google=stateError";
        }

        if (request.getParameter("error") != null) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=cancelled"
                    : "redirect:/member/login?google=cancelled";
        }

        String code = request.getParameter("code");

        if (code == null || code.trim().isEmpty()) {
            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=failed"
                    : "redirect:/member/login?google=failed";
        }

        try {
            GoogleOAuthClient.GoogleProfile profile =
                    GoogleOAuthClient.getProfile(code);

            if (linkMode) {
                HttpSession currentSession = request.getSession(false);
                String currentMemberId = currentSession == null
                        ? null
                        : (String) currentSession.getAttribute("member_id");

                if (currentMemberId == null
                        || !linkingMemberId.equals(currentMemberId)) {
                    return "redirect:/member/login";
                }

                memberService.linkSocialAccount(
                        currentMemberId,
                        "GOOGLE",
                        profile.getId()
                );

                return "redirect:/order/mypage?section=profile&social=linked";
            }

            MemberLoginInfo member =
                    memberService.loginWithGoogle(profile);

            String oldToken = findCookieValue(request, REMEMBER_COOKIE);

            if (oldToken != null) {
                try {
                    memberService.deleteRememberToken(oldToken);
                } catch (Exception e) {
                    request.getServletContext().log(
                            "기존 자동 로그인 토큰 삭제 실패",
                            e
                    );
                }
            }

            expireRememberCookie(request, response);

            HttpSession memberSession = request.getSession();
            request.changeSessionId();

            memberSession.setAttribute("member_id", member.getMemberId());
            memberSession.setAttribute("email", member.getEmail());
            memberSession.setAttribute("name", member.getName());
            memberSession.setAttribute("role", member.getRole());

            return "redirect:/home";

        } catch (Exception e) {
            request.getServletContext().log("Google 로그인/연동 처리 실패", e);

            return linkMode
                    ? "redirect:/order/mypage?section=profile&social=failed"
                    : "redirect:/member/login?google=failed";
        }
    }
    
    @DaisoGetMapping("/member/oauth/google/start")
    public String googleStart(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(true);
            boolean linkMode = "link".equals(request.getParameter("mode"));

            if (linkMode) {
                Object memberId = session.getAttribute("member_id");

                if (memberId == null) {
                    return "redirect:/member/login";
                }

                session.setAttribute(
                        "googleOAuthLinkMemberId",
                        String.valueOf(memberId)
                );
            } else {
                session.removeAttribute("googleOAuthLinkMemberId");
            }

            byte[] stateBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(stateBytes);

            String state = java.util.Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(stateBytes);

            session.setAttribute("googleOAuthState", state);

            String redirectUri =
                    "http://localhost:8080/daisomall/app/member/oauth/google/callback";

            String authUrl =
                    "https://accounts.google.com/o/oauth2/v2/auth"
                    + "?client_id=" + googleUrlEncode(
                            GoogleOAuthClient.getClientId())
                    + "&redirect_uri=" + googleUrlEncode(redirectUri)
                    + "&response_type=code"
                    + "&scope=" + googleUrlEncode("openid email profile")
                    + "&state=" + googleUrlEncode(state)
                    + "&prompt=select_account";

            return "redirect:" + authUrl;

        } catch (Exception e) {
            request.getServletContext().log("Google 로그인/연동 시작 실패", e);

            if ("link".equals(request.getParameter("mode"))) {
                return "redirect:/order/mypage?section=profile&social=failed";
            }

            return "redirect:/member/login?google=configError";
        }
    }

    private String googleUrlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }
    
    @DaisoGetMapping("/member/find-id")
    public String findIdForm() {
        return "member/find-id";
    }

    @DaisoPostMapping("/member/find-id")
    public String findId(HttpServletRequest request) {
        try {
            request.setCharacterEncoding("UTF-8");

            String name = request.getParameter("name");
            String phone = request.getParameter("phone");

            String loginId = memberService.findLoginId(name, phone);

            if (loginId == null) {
                request.setAttribute(
                        "findIdError",
                        "입력한 정보와 일치하는 회원을 찾을 수 없습니다."
                );
            } else {
                request.setAttribute("findIdResult", "찾은 아이디: " + loginId);
            }

            return "member/find-id";

        } catch (IllegalArgumentException e) {
            request.setAttribute("findIdError", e.getMessage());
            return "member/find-id";

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute(
                    "findIdError",
                    "아이디 찾기 중 오류가 발생했습니다."
            );
            return "member/find-id";
        }
    }
    
    @DaisoGetMapping("/home")
    public String home() {
        return "redirect:/products/home";
    }
    
    @DaisoGetMapping("/member/mypage")
    public String mypage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("member_id") == null) {
            return "redirect:/member/login";
        }

        request.setAttribute("memberName", session.getAttribute("name"));
        request.setAttribute("memberEmail", session.getAttribute("email"));

        return "member/mypage";
    }
    @DaisoGetMapping("/admin/dashboard")
    public String adminDashboard(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("member_id") == null
                || !"ADMIN".equalsIgnoreCase(
                        String.valueOf(session.getAttribute("role")))) {
            return "redirect:/member/login";
        }

        request.setAttribute("adminSection", "dashboard");

        try {
            AdminDashboardDAO.Summary summary = new AdminDashboardDAO().findSummary();
            java.text.NumberFormat number =
                    java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);

            request.setAttribute("dashboardProductCount", number.format(summary.productCount));
            request.setAttribute("dashboardCategoryCount", number.format(summary.categoryCount));
            request.setAttribute("dashboardOrderCount", number.format(summary.orderCount));
            request.setAttribute("dashboardPaidAmount", number.format(summary.paidAmount));
            request.setAttribute("dashboardAwaitingShipmentCount",
                    number.format(summary.awaitingShipmentCount));
            request.setAttribute("dashboardOnlineSoldOutCount",
                    number.format(summary.onlineSoldOutCount));
        } catch (javax.naming.NamingException | java.sql.SQLException e) {
            request.getServletContext().log("관리자 대시보드 집계 조회 실패", e);
            request.setAttribute("dashboardLoadFailed", true);
        }

        return "admin/dashboard";
    }
    
    @DaisoGetMapping("/member/profile")
    public String memberProfile() {
        return "redirect:/order/mypage?section=profile";
    }
    
    private static final java.security.SecureRandom PROFILE_SMS_RANDOM =
            new java.security.SecureRandom();

    private static final String PROFILE_SMS_CHALLENGE = "profileSmsChallenge";
    private static final String PROFILE_SMS_LAST_SENT = "profileSmsLastSent";
    private static final String PROFILE_VERIFIED_PHONE = "profileVerifiedPhone";

    private static final class ProfileSmsChallenge {
        final String memberId;
        final String phone;
        final String code;
        final long expiresAt;
        int attempts;

        ProfileSmsChallenge(String memberId, String phone, String code, long expiresAt) {
            this.memberId = memberId;
            this.phone = phone;
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    private static boolean validProfileCsrf(HttpServletRequest request, HttpSession session) {
        Object expected = session.getAttribute("orderCsrf");
        return expected instanceof String && expected.equals(request.getParameter("csrfToken"));
    }

    private static String profilePhone(MemberProfileInfo profile) {
        return profile == null || profile.getPhone() == null
                ? "" : profile.getPhone().replaceAll("\\D", "");
    }

    @DaisoPostMapping("/member/profile/send-code")
    @DaisoResponseBody
    public String sendProfileCode(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("member_id") instanceof String)) {
            return "다시 로그인해 주세요.";
        }
        if (!validProfileCsrf(request, session)) {
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }

        String memberId = (String) session.getAttribute("member_id");

        synchronized (session) {
            long now = System.currentTimeMillis();
            Object lastSent = session.getAttribute(PROFILE_SMS_LAST_SENT);
            if (lastSent instanceof Long && now - (Long) lastSent < 60_000L) {
                return "인증번호는 60초 후에 다시 요청할 수 있습니다.";
            }

            try {
                String phone = profilePhone(memberService.findProfileByMemberId(memberId));
                if (!phone.matches("010[0-9]{8}")) {
                    return "등록된 휴대폰 번호를 확인해 주세요.";
                }

                String code = String.format(java.util.Locale.ROOT, "%06d",
                        PROFILE_SMS_RANDOM.nextInt(1_000_000));

                session.setAttribute(PROFILE_SMS_LAST_SENT, now);
                session.removeAttribute(PROFILE_SMS_CHALLENGE);
                new SolapiSmsClient().sendVerificationCode(phone, code);

                session.setAttribute(PROFILE_SMS_CHALLENGE, new ProfileSmsChallenge(
                        memberId, phone, code, System.currentTimeMillis() + 5 * 60_000L));
                session.removeAttribute("profileVerificationMemberId");
                session.removeAttribute("profileVerificationExpiresAt");
                session.removeAttribute(PROFILE_VERIFIED_PHONE);
                return "OK";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "문자 발송이 중단되었습니다. 잠시 후 다시 시도해 주세요.";
            } catch (Exception e) {
                request.getServletContext().log("회원정보 인증 문자 발송 실패", e);
                return "문자를 보낼 수 없습니다. 잠시 후 다시 시도해 주세요.";
            }
        }
    }

    @DaisoPostMapping("/member/profile/verify-code")
    @DaisoResponseBody
    public String verifyProfileCode(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("member_id") instanceof String)) {
            return "다시 로그인해 주세요.";
        }
        if (!validProfileCsrf(request, session)) {
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }

        synchronized (session) {
            Object value = session.getAttribute(PROFILE_SMS_CHALLENGE);
            if (!(value instanceof ProfileSmsChallenge)) {
                return "인증번호를 먼저 요청해 주세요.";
            }

            ProfileSmsChallenge challenge = (ProfileSmsChallenge) value;
            if (!challenge.memberId.equals(session.getAttribute("member_id"))
                    || System.currentTimeMillis() >= challenge.expiresAt) {
                session.removeAttribute(PROFILE_SMS_CHALLENGE);
                return "인증번호가 만료되었습니다. 다시 요청해 주세요.";
            }

            if (!challenge.code.equals(request.getParameter("code"))) {
                challenge.attempts++;
                if (challenge.attempts >= 5) {
                    session.removeAttribute(PROFILE_SMS_CHALLENGE);
                    return "입력 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요.";
                }
                return "인증번호가 올바르지 않습니다.";
            }

            session.removeAttribute(PROFILE_SMS_CHALLENGE);
            session.setAttribute(PROFILE_VERIFIED_PHONE, challenge.phone);
            session.setAttribute("profileVerificationMemberId", challenge.memberId);
            session.setAttribute("profileVerificationExpiresAt",
                    System.currentTimeMillis() + 10 * 60_000L);
            return "OK";
        }
    }

    @DaisoPostMapping("/member/profile/update")
    @DaisoResponseBody
    public String updateBasicProfile(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null) {
            return "LOGIN_REQUIRED";
        }

        Object memberIdValue = session.getAttribute("member_id");
        if (!(memberIdValue instanceof String)) {
            return "LOGIN_REQUIRED";
        }
        if (!validProfileCsrf(request, session)) {
            return "페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }
        String memberId = (String) memberIdValue;
        Object verifiedMemberId =
                session.getAttribute("profileVerificationMemberId");
        Object expiresAtValue =
                session.getAttribute("profileVerificationExpiresAt");

        if (!memberId.equals(verifiedMemberId)
                || !(expiresAtValue instanceof Long)
                || ((Long) expiresAtValue) < System.currentTimeMillis()) {
            return "VERIFY_REQUIRED";
        }

        try {
            String name = request.getParameter("name");
            MemberProfileInfo currentProfile = memberService.findProfileByMemberId(memberId);
            String phone = profilePhone(currentProfile);
            if (!phone.matches("010[0-9]{8}")
                    || !phone.equals(session.getAttribute(PROFILE_VERIFIED_PHONE))) {
                return "휴대폰 인증을 다시 진행해 주세요.";
            }
            String birthDate = request.getParameter("birthDate");
            String gender = request.getParameter("gender");

            memberService.updateBasicProfile(
                    memberId,
                    name,
                    phone,
                    birthDate,
                    gender
            );

            session.setAttribute("name", name.trim());
            session.removeAttribute("profileVerificationMemberId");
            session.removeAttribute("profileVerificationExpiresAt");
            session.removeAttribute(PROFILE_VERIFIED_PHONE);

            return "OK";

        } catch (IllegalArgumentException e) {
            return e.getMessage();

        } catch (Exception e) {
            request.getServletContext().log("기본정보 변경 실패", e);
            return "회원정보 변경 중 오류가 발생했습니다.";
        }
    }
    
    @DaisoGetMapping("/member/find-password")
    public String findPasswordForm() {
        return "member/find-password";
    }

    @DaisoPostMapping("/member/find-password")
    public String findPassword(HttpServletRequest request) {
        try {
            request.setCharacterEncoding("UTF-8");

            String email = request.getParameter("email");
            String name = request.getParameter("name");
            String phone = request.getParameter("phone");
            String newPassword = request.getParameter("newPassword");
            String confirmPassword = request.getParameter("confirmPassword");

            if (newPassword == null || !newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException(
                        "새 비밀번호와 비밀번호 확인이 일치하지 않습니다."
                );
            }

            memberService.resetPassword(email, name, phone, newPassword);

            return "redirect:/member/login?passwordReset=success";

        } catch (IllegalArgumentException e) {
            request.setAttribute("resetError", e.getMessage());
            return "member/find-password";

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute(
                    "resetError",
                    "비밀번호 변경 중 오류가 발생했습니다."
            );
            return "member/find-password";
        }
    }
   
    @DaisoPostMapping("/member/logout")
    public String logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String token = findCookieValue(request, REMEMBER_COOKIE);

        if (token != null) {
            try {
                memberService.deleteRememberToken(token);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        expireRememberCookie(request, response);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return "redirect:/products/home?logout=success";
    }
    
    private String findCookieValue(
            HttpServletRequest request, String cookieName) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private void addRememberCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String token) {

        Cookie cookie = new Cookie(REMEMBER_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(getCookiePath(request));
        cookie.setMaxAge(REMEMBER_COOKIE_AGE);

        response.addCookie(cookie);
    }

    private void expireRememberCookie(
            HttpServletRequest request,
            HttpServletResponse response) {

        Cookie cookie = new Cookie(REMEMBER_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(getCookiePath(request));
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }

    private String getCookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();

        if (contextPath == null || contextPath.isEmpty()) {
            return "/";
        }

        return contextPath + "/";
    }
    @DaisoPostMapping("/member/profile/email")
    @DaisoResponseBody
    public String updateProfileEmail(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("member_id") == null) {
            return "로그인 상태를 확인해 주세요.";
        }

        String memberId = String.valueOf(session.getAttribute("member_id"));
        String newEmail = request.getParameter("email");

        try {
            String updatedEmail = memberService.changeEmail(memberId, newEmail);

            // 다음 요청에서도 변경된 이메일을 사용하도록 세션 갱신
            session.setAttribute("email", updatedEmail);

            return "OK";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            request.getServletContext().log("이메일 변경 실패", e);
            return "이메일 변경 중 오류가 발생했습니다.";
        }
    }
    @DaisoPostMapping("/member/profile/email/check")
    @DaisoResponseBody
    public String checkProfileEmail(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("member_id") == null) {
            return "LOGIN_REQUIRED";
        }

        String memberId = String.valueOf(session.getAttribute("member_id"));
        String email = request.getParameter("email");

        try {
            return memberService.isEmailAvailableForChange(memberId, email)
                    ? "available"
                    : "duplicate";
        } catch (IllegalArgumentException e) {
            return "invalid";
        } catch (Exception e) {
            request.getServletContext().log("이메일 중복 확인 실패", e);
            return "error";
        }
    }
    @DaisoPostMapping("/member/profile/password")
    @DaisoResponseBody
    public String updateProfilePassword(
            HttpServletRequest request,
            HttpServletResponse response)
            throws java.io.UnsupportedEncodingException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("member_id") == null) {
            return "LOGIN_REQUIRED";
        }

        String memberId = String.valueOf(session.getAttribute("member_id"));
        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        try {
            memberService.changePassword(
                    memberId,
                    currentPassword,
                    newPassword,
                    confirmPassword
            );

            // DB에서 기존 자동 로그인 토큰을 지웠으므로 브라우저 쿠키도 만료
            expireRememberCookie(request, response);

            return "OK";

        } catch (IllegalArgumentException e) {
            return e.getMessage();

        } catch (Exception e) {
            request.getServletContext().log("비밀번호 변경 실패", e);
            return "비밀번호 변경 중 오류가 발생했습니다.";
        }
    }
    @DaisoPostMapping("/member/profile/social/unlink")
    @DaisoResponseBody
    public String unlinkProfileSocialAccount(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("member_id") == null) {
            return "LOGIN_REQUIRED";
        }

        String memberId = String.valueOf(session.getAttribute("member_id"));
        String provider = request.getParameter("provider");

        try {
            memberService.unlinkSocialAccount(memberId, provider);
            return "OK";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            request.getServletContext().log("SNS 연동 해제 실패", e);
            return "SNS 연동 해제 중 오류가 발생했습니다.";
        }
    }
}