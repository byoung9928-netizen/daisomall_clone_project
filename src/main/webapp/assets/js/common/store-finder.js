(() => {
    const picker = document.querySelector('[data-store-picker]');
    if (!picker || typeof picker.showModal !== 'function') return;

    picker.removeAttribute('open');
    picker.showModal();

    picker.addEventListener('cancel', event => {
        event.preventDefault();
        location.assign(picker.querySelector('[data-picker-close]').href);
    });
})();
(() => {
    const containers = [...document.querySelectorAll('[data-naver-store-map]')]
        .filter(container => container.dataset.clientId && container.dataset.bounds);

    if (!containers.length) return;

    function showFallback(container) {
        container.hidden = true;
        const fallback = container.parentElement.querySelector('iframe');
        if (fallback) fallback.hidden = false;
    }

	let authFailed = false;

	window.navermap_authFailure = () => {
	    authFailed = true;
	    containers.forEach(showFallback);
	};

    const sdk = document.createElement('script');
    const sdkUrl = new URL('https://oapi.map.naver.com/openapi/v3/maps.js');
    sdkUrl.searchParams.set('ncpKeyId', containers[0].dataset.clientId);
    sdk.src = sdkUrl.toString();

    sdk.onload = () => {
       if (authFailed || !window.naver || !window.naver.maps || !window.naver.maps.Map) return;

        containers.forEach(container => {
            const [west, south, east, north] =
                container.dataset.bounds.split(',').map(Number);

            const center = new naver.maps.LatLng(
                (south + north) / 2,
                (west + east) / 2
            );

            container.hidden = false;

            try {
                new naver.maps.Map(container, { center, zoom: 14 });
                const fallback = container.parentElement.querySelector('iframe');
                if (fallback) fallback.hidden = true;
            } catch (error) {
                showFallback(container);
            }
        });
    };

    document.head.appendChild(sdk);
})();
