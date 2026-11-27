(() => {
    const open = document.getElementById("open-admin-chat");
    const dialog = document.getElementById("admin-chat-dialog");
    if (!open || !dialog) return;

    const form = document.getElementById("admin-chat-form");
    const input = document.getElementById("admin-chat-question");
    const log = document.getElementById("admin-chat-log");
    const status = document.getElementById("admin-chat-status");
    const quickButtons = dialog.querySelectorAll("[data-question]");
    const send = form.querySelector("button");
    let sending = false;

    open.addEventListener("click", () => {
        dialog.showModal();
        input.focus();
    });

    function appendMessage(message, fromUser = false, href, label) {
        const bubble = document.createElement("p");
        bubble.className = fromUser ? "ai-chat-bubble ai-chat-user" : "ai-chat-bubble";
        bubble.textContent = message;
        if (href && label) {
            const link = document.createElement("a");
            link.href = href;
            link.textContent = label;
            bubble.append(document.createElement("br"), link);
        }
        log.append(bubble);
        log.scrollTop = log.scrollHeight;
    }

    async function ask(value) {
        const question = value.trim();
        if (sending || !question) return;

        sending = true;
        send.disabled = true;
        quickButtons.forEach(button => { button.disabled = true; });
        status.textContent = "확인 중입니다.";
        appendMessage(question, true);
        try {
            const response = await fetch(form.action, {
                method: "POST",
                headers: { "X-Daiso-Request": "admin-chat" },
                body: new URLSearchParams({ question })
            });
            if (response.status === 403) {
                appendMessage("총괄 관리자 계정으로 다시 로그인해 주세요.");
                return;
            }
            if (!response.ok) throw new Error("관리 도우미 응답 실패");

            const reply = await response.json();
            appendMessage(reply.message, false, reply.href, reply.label);
        } catch {
            appendMessage("정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        } finally {
            status.textContent = "";
            sending = false;
            send.disabled = false;
            quickButtons.forEach(button => { button.disabled = false; });
        }
    }

    form.addEventListener("submit", event => {
        event.preventDefault();
        const question = input.value.trim();
        if (!question || sending) return;
        input.value = "";
        void ask(question);
    });

    quickButtons.forEach(button => {
        button.addEventListener("click", () => { void ask(button.dataset.question); });
    });
})();
