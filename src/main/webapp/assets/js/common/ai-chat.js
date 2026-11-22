(() => {
    const open = document.getElementById('open-ai-chat');
    const dialog = document.getElementById('ai-chat-dialog');
    if (!open || !dialog) return;

    const form = document.getElementById('ai-chat-form');
    const input = document.getElementById('ai-chat-question');

    open.addEventListener('click', () => {
        if (!dialog.open) dialog.showModal();
        if (input) input.focus();
    });

    if (!form) return;

    const log = document.getElementById('ai-chat-log');
    const status = document.getElementById('ai-chat-status');
    const send = form.querySelector('button[type="submit"]');
    const history = [];

    function addBubble(text, isUser) {
        const bubble = document.createElement('p');
        bubble.className = isUser ? 'ai-chat-bubble ai-chat-user' : 'ai-chat-bubble';
        bubble.textContent = text;
        log.appendChild(bubble);
        log.scrollTop = log.scrollHeight;
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();

        const question = input.value.trim();
        if (!question || send.disabled) return;

        const data = new URLSearchParams();
        data.set('question', question);
        data.set('history', history.join('\n').slice(-1600));

        addBubble(question, true);
        input.value = '';
        send.disabled = true;
        status.textContent = '답변을 준비하고 있어요.';

        try {
            const response = await fetch(form.action, {
                method: 'POST',
                headers: { 'X-Daiso-Request': 'stock-chat' },
                body: data
            });

            const answer = (await response.text()).trim();
            addBubble(answer || '답변이 비어 있어요. 다시 질문해 주세요.', false);

            if (response.ok && answer) {
                history.push('사용자: ' + question + '\nAI: ' + answer);
                if (history.length > 3) history.shift();
            }
        } catch (error) {
            addBubble('연결에 실패했어요. 잠시 후 다시 질문해 주세요.', false);
        } finally {
            send.disabled = false;
            status.textContent = '';
            if (dialog.open) input.focus();
        }
    });
})();