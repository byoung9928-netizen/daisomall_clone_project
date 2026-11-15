const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const currentBase = process.env.FRONTEND_BASE_URL || "http://127.0.0.1:18089/daisomall/";
const baselineBase = process.env.BASELINE_BASE_URL || "http://127.0.0.1:18090/daisomall/";
const editForms = process.argv.includes("--edit");
const routeFilter = process.argv.slice(2).find(value => !value.startsWith("--"));

// Run the unchanged application on 18090 and the refactored application on 18089.
// Each page receives the same browser storage, including identical order IDs/dates.
async function main() {
    const browser = await chromium.launch({ channel: "chrome", headless: true });
    const output = fs.mkdtempSync(path.join(os.tmpdir(), "daisomall-view-comparison-"));
    const results = [];
    try {
        for (const width of [1440, 1280, 390]) {
            const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: "reduce" });
            const before = await context.newPage();
            const after = await context.newPage();
            const errors = [];
            after.on("pageerror", error => errors.push(error.message));
            await before.goto(baselineBase);
            const seed = await before.evaluate(async () => {
                const r = await window.Daiso.ready;
                r.login("admin@example.test", "demo");
                const product = r.queryProducts({ availableOnly: true })[0];
                r.addCart(product.id, 1);
                const paid = r.placeOrder({ recipient: "체험 회원", phone: "010-1234-5678", postcode: "12345", address: "테스트 주소", outcome: "success", token: "view-paid" });
                r.addCart(product.id, 1);
                const failed = r.placeOrder({ recipient: "체험 회원", phone: "010-1234-5678", postcode: "12345", address: "테스트 주소", outcome: "failure", token: "view-failed" });
                return { local: { ...localStorage }, session: { ...sessionStorage }, product: product.id, paid: paid.id, failed: failed.id };
            });
            await context.addInitScript(seed => {
                for (const [key, value] of Object.entries(seed.local)) localStorage.setItem(key, value);
                for (const [key, value] of Object.entries(seed.session)) sessionStorage.setItem(key, value);
            }, seed);
            const routes = ["", "list.jsp", "search.jsp?q=소스병", "product.jsp?id=" + seed.product,
                "login.jsp", "signup.jsp", "mypage.jsp", "profile.jsp", "cart.jsp", "checkout.jsp", "orders.jsp",
                "order.jsp?id=" + seed.paid, "payment-success.jsp?id=" + seed.paid, "payment-failure.jsp?id=" + seed.failed,
                ...["dashboard", "categories", "products", "inventory", "stores", "orders", "users"].map(view => "admin/?view=" + view),
                "tests/common-layout.jsp"];
            const selectedRoutes = routes.filter(route => (!routeFilter || route === routeFilter)
                && (!editForms || /^admin\/\?view=(categories|products|inventory|stores|users)$/.test(route)));
            for (const [index, route] of selectedRoutes.entries()) {
                const screenshots = [];
                const texts = [];
                const layouts = [];
                for (const [page, base] of [[before, baselineBase], [after, currentBase]]) {
                    assert.equal((await page.goto(base + route)).status(), 200, route);
                    await page.evaluate(async () => { await window.Daiso.ready; await document.fonts.ready; });
                    await page.waitForTimeout(100);
                    if (editForms) {
                        await page.locator("tbody button:enabled").first().click();
                        await page.locator(".admin-editor-form").waitFor({ state: "visible" });
                    }
                    await page.evaluate(async () => Promise.all([...document.images].filter(image => image.complete && image.naturalWidth).map(image => image.decode().catch(() => {}))));
                    texts.push((await page.locator("main").innerText()).replace(/\s+/g, " ").trim());
                    layouts.push(await page.locator("main").evaluate(main => [...main.querySelectorAll("h1,h2,h3,p,a,img,input,select,button,label,section,article,table,th,td,ul,li,div")].filter(node => node.getClientRects().length > 0).map(node => {
                        const box = node.getBoundingClientRect();
                        return [node.tagName, node.className, ...[box.x, box.y, box.width, box.height].map(value => Math.round(value * 100) / 100), "value" in node ? node.value : null];
                    })));
                    screenshots.push(await page.screenshot({ animations: "disabled" }));
                }
                const equal = screenshots[0].equals(screenshots[1]);
                const layoutEqual = JSON.stringify(layouts[0]) === JSON.stringify(layouts[1]);
                results.push({ width, route, textEqual: texts[0] === texts[1], layoutEqual, pixelsEqual: equal });
                if (!equal) {
                    fs.writeFileSync(path.join(output, `${width}-${index}-before.png`), screenshots[0]);
                    fs.writeFileSync(path.join(output, `${width}-${index}-after.png`), screenshots[1]);
                }
            }
            console.log(`Compared ${width}px`);
            assert.deepEqual(errors, []);
            await context.close();
        }
    } finally { await browser.close(); }
    const mismatches = results.filter(row => !row.textEqual || !row.layoutEqual || !row.pixelsEqual);
    console.log(JSON.stringify({ checked: results.length, mismatches, output }, null, 2));
    assert.equal(mismatches.length, 0, "View differs from baseline; inspect comparison PNGs");
}
main().catch(error => { console.error(error); process.exitCode = 1; });
