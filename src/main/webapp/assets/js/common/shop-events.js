(() => {
    "use strict";
    const app = window.Daiso;
    app.features = { catalog: true, orders: true, admin: true };
    app.ui.orderStatus = status => ({ PAID: "결제 완료", PAYMENT_FAILED: "결제 실패", PREPARING: "배송 준비", SHIPPED: "배송 중", DELIVERED: "배송 완료", CANCELLED: "주문 취소" })[status] || status;
    document.querySelectorAll("[data-navigation-key]").forEach((item) => {
        const key = item.dataset.navigationKey;
        const choices = { season: "newest" };
        if (choices[key]) {
            item.removeAttribute("data-unavailable");
            item.addEventListener("click", () => {
                location.href = app.url("list.jsp", { sort: choices[key], collection: key });
            });
        }
    });

    app.ready.then(repository => {
        function refreshHeader() {
            const member = repository.getSession();
            const login = document.querySelector('[data-member-route="login"]');
            if (login) {
                login.querySelector(".account-label").textContent = member ? "로그아웃" : "로그인·회원";
                login.title = member ? member.name + " · 로그아웃" : "로그인·회원";
            }
        }
        document.querySelectorAll("[data-member-route]").forEach(control => {
            control.removeAttribute("data-unavailable");
            control.addEventListener("click", () => {
                if (control.dataset.memberRoute === "login" && repository.getSession()) { repository.logout(); location.href = app.url(""); }
                else location.href = app.url(control.dataset.memberRoute + ".jsp");
            });
        });
        document.querySelectorAll("[data-shopping-route]").forEach(control => {
            control.removeAttribute("data-unavailable");
            control.addEventListener("click", () => { 
				/*location.href = app.url(control.dataset.shoppingRoute + ".jsp"); */
				const route = control.dataset.shoppingRoute;
				
				if (route === "cart"){
					location.href=app.url("app/cart");
				} else if (route === "orders"){
					location.href=app.url("app/order/list");
				}
				
			});
        });
        window.addEventListener("mock:session", refreshHeader);
        refreshHeader();
    }).catch(() => {}); // 각 페이지와 내비게이션이 오류·재시도 화면을 표시합니다.
})();
