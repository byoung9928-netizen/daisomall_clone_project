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

	function addBubble(text, isUser, products = []) {
	    const bubble = document.createElement(products.length ? 'div' : 'p');
	    bubble.className = isUser ? 'ai-chat-bubble ai-chat-user' : 'ai-chat-bubble';
	    bubble.textContent = text;

	    if (products.length) {
	        const list = document.createElement('ul');
	        list.className = 'ai-chat-product-list';

	        for (const product of products) {
	            const item = document.createElement('li');
	            const link = document.createElement('a');
	            const url = new URL(form.dataset.productDetailUrl, window.location.origin);
	            url.searchParams.set('id', product.id);

	            link.href = url.href;
	            link.textContent = product.name;
	            item.append(link, document.createTextNode(` (품번 ${product.code})`));
	            list.appendChild(item);
	        }

	        bubble.appendChild(list);
	    }

	    log.appendChild(bubble);
	    log.scrollTop = log.scrollHeight;
	}

	async function sendQuestion(question, faqId = '') {
	if (!question || send.disabled) return;

	const data = new URLSearchParams();
	data.set('question', question);
	data.set('history', history.join('\n').slice(-1600));

	if (faqId) {
	    data.set('faqId', faqId);
	}

        addBubble(question, true);
        if (!faqId) input.value = '';
        send.disabled = true;
        status.textContent = '답변을 준비하고 있어요.';

        try {
            const response = await fetch(form.action, {
                method: 'POST',
                headers: { 'X-Daiso-Request': 'stock-chat' },
                body: data
            });

			const hasProducts = response.headers.get('X-Daiso-Chat-Format') === 'product-choices';
			const reply = hasProducts
			    ? await response.json()
			    : { answer: await response.text(), products: [] };

			const answer = reply.answer.trim();
			addBubble(answer || '답변이 비어 있어요. 다시 질문해 주세요.', false, reply.products);

            if (response.ok && answer && response.headers.get('X-Daiso-Chat-Private') !== 'true') {
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
		}

		form.addEventListener('submit', event => {
		    event.preventDefault();
		    sendQuestion(input.value.trim());
		});

		const shippingButton = document.getElementById('ai-chat-faq-shipping');
		if (shippingButton) {
		    shippingButton.addEventListener('click', () => {
		        sendQuestion('배송비 얼마야?', 'shipping_fee');
		    });
		}
})();