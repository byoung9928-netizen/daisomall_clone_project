package daiso.member.controller;

import javax.servlet.http.HttpServletRequest;

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
}