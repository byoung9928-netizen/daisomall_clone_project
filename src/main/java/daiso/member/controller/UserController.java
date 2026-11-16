package daiso.member.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;

@DaisoController
public class UserController {

    private final MemberService memberService = new MemberService();

    @DaisoGetMapping("/member/signup")
    public String signupForm() {
        return "member/signup";
    }

    @DaisoPostMapping("/member/signup")
    public String signup(HttpServletRequest request) {
        try {
            request.setCharacterEncoding("UTF-8");

            String email = request.getParameter("email");
            String password = request.getParameter("password");
            String name = request.getParameter("name");
            String phone = request.getParameter("phone");

            memberService.register(email, password, name, phone);

            return "redirect:/member/login?signup=success";

        } catch (IllegalArgumentException e) {
            request.setAttribute("signupError", e.getMessage());
            return "member/signup";

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("signupError", "가입 처리 중 오류가 발생했습니다.");
            return "member/signup";
        }
    }

    @DaisoGetMapping("/member/login")
    public String loginForm() {
        return "member/login";
    }
    
    @DaisoGetMapping("/home")
    public String home() {
        return "shop/home";
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
    
    @DaisoPostMapping("/member/login")
    public String login(HttpServletRequest request) {
        try {
            request.setCharacterEncoding("UTF-8");

            String email = request.getParameter("email");
            String password = request.getParameter("password");

            MemberLoginInfo member = memberService.login(email, password);

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
                    "로그인 처리 중 오류가 발생했습니다."
            );
            return "member/login";
        }
    }
   
    @DaisoPostMapping("/member/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return "redirect:/home?logout=success";
    }
}