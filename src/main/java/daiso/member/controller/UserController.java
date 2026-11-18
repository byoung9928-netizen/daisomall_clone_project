package daiso.member.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import daiso.common.annotation.DaisoResponseBody;

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
            request.setAttribute("loginNotice", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.");
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
    
    @DaisoGetMapping("/member/oauth/kakao/callback")
    public String kakaoCallback(
            HttpServletRequest request,
            HttpServletResponse response) {
        String error = request.getParameter("error");

        if (error != null) {
            request.setAttribute(
                    "loginError",
                    "카카오 로그인이 취소되었거나 실패했습니다."
            );
            return "member/login";
        }

        String code = request.getParameter("code");

        if (code == null || code.trim().isEmpty()) {
            request.setAttribute(
                    "loginError",
                    "카카오 인증 코드를 받지 못했습니다."
            );
            return "member/login";
        }

        try {
            KakaoOAuthClient.KakaoProfile profile =
                    new KakaoOAuthClient().getProfile(code);

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
                    e.printStackTrace();
                }
            }		

            expireRememberCookie(request, response);

            HttpSession session = request.getSession();
            request.changeSessionId();

            session.setAttribute("member_id", member.getMemberId());
            session.setAttribute("email", member.getEmail());
            session.setAttribute("name", member.getName());
            session.setAttribute("role", member.getRole());

            return "redirect:/home";
            
        } catch (IllegalArgumentException e) {
            request.setAttribute("loginError", e.getMessage());
            return "member/login"; 

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute(
                    "loginError",
                    "카카오 로그인 처리 중 오류가 발생했습니다."
            );
            return "member/login";
        }
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

            String email = memberService.findEmail(name, phone);

            if (email == null) {
                request.setAttribute(
                        "findIdError",
                        "입력한 정보와 일치하는 회원을 찾을 수 없습니다."
                );
            } else {
                request.setAttribute("findIdResult", "찾은 아이디: " + email);
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
    
    @DaisoGetMapping("/member/profile")
    public String memberProfile() {
        return "redirect:/order/mypage?section=profile";
    }
    
    @DaisoPostMapping("/member/profile/verify-demo")
    @DaisoResponseBody
    public String verifyBasicProfileDemo(HttpServletRequest request)
            throws java.io.UnsupportedEncodingException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null) {
            return "LOGIN_REQUIRED";
        }

        Object memberIdValue = session.getAttribute("member_id");
        if (!(memberIdValue instanceof String)
                || ((String) memberIdValue).isBlank()) {
            return "LOGIN_REQUIRED";
        }

        String code = request.getParameter("code");
        if (!"123456".equals(code)) {
            return "INVALID_CODE";
        }

        session.setAttribute(
                "profileVerificationMemberId",
                memberIdValue
        );
        session.setAttribute(
                "profileVerificationExpiresAt",
                System.currentTimeMillis() + 10 * 60 * 1000L
        );

        return "OK";
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
            String phone = request.getParameter("phone");
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
}