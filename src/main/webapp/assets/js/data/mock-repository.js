(function () {
    "use strict";

    const script = document.currentScript;
    const base = new URL("../../../", script.src);
    const storageKey = "daisomall.mock.v1";
    const copy = function (value) { return JSON.parse(JSON.stringify(value)); };

    function readJson(file) {
        return fetch(new URL("assets/mock/" + file, base)).then(function (response) {
            if (!response.ok) {
                throw new Error("초기 데이터를 불러오지 못했습니다: " + response.status);
            }
            return response.json();
        });
    }

    function readState() {
        const raw = localStorage.getItem(storageKey);
        if (!raw) {
            return { schemaVersion: 1, productOverrides: {}, categoryOverrides: {}, createdProducts: [], carts: {}, orders: [], members: [] };
        }
        const state = JSON.parse(raw);
        if (state.schemaVersion !== 1 || !state.productOverrides || !Array.isArray(state.orders)) {
            throw new Error("Mock 저장 형식이 올바르지 않습니다. Mock 데이터 초기화가 필요합니다.");
        }
        return state;
    }

    function writeState(state) {
        localStorage.setItem(storageKey, JSON.stringify(state));
        window.dispatchEvent(new Event("mock:changed"));
    }

    function setStoreStock(stocks, location, quantity) {
        const updated = Object.assign({}, stocks);
        updated[location] = quantity;
        return updated;
    }

    window.Daiso = {
        base: base,
        storageKey: storageKey,
        url: function (file, params) {
            params = params === undefined ? {} : params;
            const url = new URL(file, base);
            Object.keys(params).forEach(function (key) {
                const value = params[key];
                if (value !== undefined && value !== null && value !== "") {
                    url.searchParams.set(key, value);
                }
            });
            return url.href;
        },
    };

    window.Daiso.ready = Promise.all([readJson("categories.json"), readJson("products.json"), readJson("members.json"), readJson("stores.json")]).then(function (data) {
        const categoryData = data[0];
        const productData = data[1];
        const memberData = data[2];
        const storeData = data[3];
        const repository = {
            getCategories: function () {
                const state = readState();
                return copy(categoryData.categories.map(function (category) { return Object.assign({}, category, state.categoryOverrides[category.id]); }));
            },
            getVisibleCategories: function () {
                const categories = repository.getCategories();
                const visibleIds = new Set();
                for (let level = 1; level <= 3; level += 1) {
                    categories.forEach(function (category) {
                        if (category.level === level && category.visible !== false && (!category.parentId || visibleIds.has(category.parentId))) {
                            visibleIds.add(category.id);
                        }
                    });
                }
                return categories.filter(function (category) { return visibleIds.has(category.id); });
            },
            getProducts: function () {
                const state = readState();
                return copy(productData.products.concat(state.createdProducts).map(function (product) { return Object.assign({}, product, state.productOverrides[product.id]); }));
            },
            getProduct: function (id) {
                return repository.getProducts().find(function (product) { return product.id === id; }) || null;
            },
            queryProducts: function (options) {
                options = options === undefined ? {} : options;
                const categoryId = options.categoryId;
                const query = options.query === undefined ? "" : options.query;
                const sort = options.sort === undefined ? "recommended" : options.sort;
                const maxPrice = options.maxPrice === undefined ? Infinity : options.maxPrice;
                const availableOnly = options.availableOnly === undefined ? false : options.availableOnly;
                const categories = repository.getVisibleCategories();
                const descendants = new Set(categoryId && categories.some(function (category) { return category.id === categoryId; }) ? [categoryId] : categoryId ? [] : categories.map(function (category) { return category.id; }));
                for (let level = 1; level <= 3; level += 1) {
                    categories.forEach(function (category) {
                        if (descendants.has(category.parentId)) {
                            descendants.add(category.id);
                        }
                    });
                }
                const keyword = query.trim().toLocaleLowerCase();
                const matches = repository.getProducts().filter(function (product) { return descendants.has(product.categoryId)
                    && (product.name + " " + product.brand).toLocaleLowerCase().includes(keyword)
                    && product.price <= Number(maxPrice)
                    && (!availableOnly || product.status === "AVAILABLE" && product.onlineStock > 0); });
                const comparators = {
                    recommended: function (left, right) { return right.sales - left.sales; },
                    priceAsc: function (left, right) { return left.price - right.price; },
                    priceDesc: function (left, right) { return right.price - left.price; },
                    newest: function (left, right) { return right.createdAt.localeCompare(left.createdAt); },
                    rating: function (left, right) { return right.rating - left.rating; },
                };
                return matches.sort(comparators[sort] || comparators.recommended);
            },
            getMembers: function () {
                const state = readState();
                const members = new Map(memberData.members.map(function (member) { return [member.id, member]; }));
                state.members.forEach(function (member) { return members.set(member.id, member); });
                return copy(Array.from(members.values()));
            },
            getSession: function () {
                const id = sessionStorage.getItem("daisomall.mock.session");
                return repository.getMembers().find(function (member) { return member.id === id && member.status === "ACTIVE"; }) || null;
            },
            login: function (email, password) {
                const member = repository.getMembers().find(function (item) { return item.email === email.trim().toLowerCase(); });
                if (!member || member.status !== "ACTIVE" || typeof password !== "string" || password.length < 4) {
                    throw new Error("체험 가능한 회원 이메일과 4자 이상의 임의 비밀번호를 입력해 주세요.");
                }
                const state = readState();
                if (state.carts.guest && state.carts.guest.length) {
                    const merged = new Map((state.carts[member.id] || []).map(function (line) { return [line.productId, line.quantity]; }));
                    state.carts.guest.forEach(function (line) { return merged.set(line.productId, Math.min(99, (merged.get(line.productId) || 0) + line.quantity)); });
                    state.carts[member.id] = Array.from(merged, function (line) { return { productId: line[0], quantity: line[1] }; });
                    delete state.carts.guest;
                    writeState(state);
                }
                sessionStorage.setItem("daisomall.mock.session", member.id);
                window.dispatchEvent(new Event("mock:session"));
                return copy(member);
            },
            logout: function () {
                sessionStorage.removeItem("daisomall.mock.session");
                window.dispatchEvent(new Event("mock:session"));
            },
            register: function (values) {
                const email = values.email;
                const name = values.name;
                const phone = values.phone;
                const password = values.password;
                const normalizedEmail = email.trim().toLowerCase();
                if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail) || !name.trim()
                        || !/^[0-9 -]{8,20}$/.test(phone) || password.length < 4) {
                    throw new Error("이메일, 이름, 전화번호와 4자 이상 비밀번호를 확인해 주세요.");
                }
                if (repository.getMembers().some(function (member) { return member.email === normalizedEmail; })) {
                    throw new Error("이미 등록된 이메일입니다.");
                }
                const state = readState();
                const member = { id: "member-" + crypto.randomUUID(), email: normalizedEmail, name: name.trim(), phone: phone, role: "MEMBER", status: "ACTIVE" };
                state.members.push(member);
                writeState(state);
                return copy(member);
            },
            saveProfile: function (values) {
                const name = values.name;
                const phone = values.phone;
                const member = repository.getSession();
                if (!member) {
                    throw new Error("Mock 로그인이 필요합니다.");
                }
                if (!name.trim() || !/^[0-9 -]{8,20}$/.test(phone)) {
                    throw new Error("이름과 전화번호를 확인해 주세요.");
                }
                const updated = Object.assign({}, member, { name: name.trim(), phone: phone });
                const state = readState();
                state.members = state.members.filter(function (item) { return item.id !== member.id; });
                state.members.push(updated);
                writeState(state);
                return copy(updated);
            },
            getOwnerId: function () {
                const member = repository.getSession();
                return member ? member.id : "guest";
            },
            getCart: function () {
                const state = readState();
                return (state.carts[repository.getOwnerId()] || []).map(function (line) {
                    const product = repository.getProduct(line.productId);
                    return Object.assign({}, line, {
                        product: product,
                        lineTotal: product ? product.price * line.quantity : 0,
                        purchasable: Boolean(product && product.status === "AVAILABLE" && product.onlineStock >= line.quantity)
                    });
                });
            },
            addCart: function (productId, quantity) {
                quantity = quantity === undefined ? 1 : quantity;
                repository.validateQuantity(productId, quantity);
                const state = readState();
                const owner = repository.getOwnerId();
                const lines = state.carts[owner] || [];
                const existing = lines.find(function (line) { return line.productId === productId; });
                const nextQuantity = (existing ? existing.quantity : 0) + quantity;
                repository.validateQuantity(productId, nextQuantity);
                if (existing) {
                    existing.quantity = nextQuantity;
                } else {
                    lines.push({ productId: productId, quantity: quantity });
                }
                state.carts[owner] = lines;
                writeState(state);
            },
            validateQuantity: function (productId, quantity) {
                const product = repository.getProduct(productId);
                if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) {
                    throw new Error("수량은 1~99 사이의 정수여야 합니다.");
                }
                if (!product || product.status !== "AVAILABLE" || quantity > product.onlineStock) {
                    throw new Error("구매 가능한 상태와 온라인 재고를 확인해 주세요.");
                }
            },
            setCartQuantity: function (productId, quantity) {
                repository.validateQuantity(productId, quantity);
                const state = readState();
                const line = (state.carts[repository.getOwnerId()] || []).find(function (item) { return item.productId === productId; });
                if (!line) {
                    throw new Error("장바구니에서 상품을 찾을 수 없습니다.");
                }
                line.quantity = quantity;
                writeState(state);
            },
            removeCart: function (productId) {
                const state = readState();
                const owner = repository.getOwnerId();
                state.carts[owner] = (state.carts[owner] || []).filter(function (line) { return line.productId !== productId; });
                writeState(state);
            },
            cartTotals: function () {
                const items = repository.getCart();
                const subtotal = items.reduce(function (sum, line) { return sum + line.lineTotal; }, 0);
                const shipping = subtotal > 0 && subtotal < 30000 ? 3000 : 0;
                return { subtotal: subtotal, shipping: shipping, total: subtotal + shipping };
            },
            placeOrder: function (values) {
                const recipient = values.recipient;
                const phone = values.phone;
                const postcode = values.postcode;
                const address = values.address;
                const memo = values.memo === undefined ? "" : values.memo;
                const outcome = values.outcome;
                const token = values.token;
                const state = readState();
                const owner = repository.getOwnerId();
                const previous = state.orders.find(function (order) { return order.checkoutToken === token && order.memberId === owner; });
                if (previous) {
                    return copy(previous);
                }
                if (!recipient || !recipient.trim() || !/^[0-9 -]{8,20}$/.test(phone || "")
                        || !/^\d{5}$/.test(postcode || "") || !address || address.trim().length < 5
                        || !["success", "failure"].includes(outcome) || !token) {
                    throw new Error("배송 정보와 모의 결제 결과를 확인해 주세요.");
                }
                const cart = repository.getCart();
                if (!cart.length || cart.some(function (line) { return !line.purchasable; })) {
                    throw new Error("장바구니와 구매 가능한 재고를 다시 확인해 주세요.");
                }
                cart.forEach(function (line) { return repository.validateQuantity(line.productId, line.quantity); });
                const totals = repository.cartTotals();
                const order = {
                    id: "ORDER-" + Date.now() + "-" + crypto.randomUUID().slice(0, 8),
                    checkoutToken: token,
                    memberId: owner,
                    createdAt: new Date().toISOString(),
                    status: outcome === "success" ? "PAID" : "PAYMENT_FAILED",
                    recipient: recipient.trim(),
                    phone: phone,
                    postcode: postcode,
                    address: address.trim(),
                    memo: memo.trim(),
                    items: cart.map(function (line) { return { productId: line.productId, name: line.product.name, image: line.product.image, price: line.product.price, quantity: line.quantity, lineTotal: line.lineTotal }; }),
                    totals: totals,
                    mockPayment: true,
                };
                if (outcome === "success") {
                    cart.forEach(function (line) {
                        state.productOverrides[line.productId] = Object.assign({}, state.productOverrides[line.productId], { onlineStock: line.product.onlineStock - line.quantity });
                    });
                    state.carts[owner] = [];
                }
                state.orders.unshift(order);
                writeState(state);
                return copy(order);
            },
            getOrders: function () {
                return copy(readState().orders.filter(function (order) { return order.memberId === repository.getOwnerId(); }));
            },
            getOrder: function (id) {
                return repository.getOrders().find(function (order) { return order.id === id; }) || null;
            },
            requireAdmin: function () {
                const member = repository.getSession();
                if (!member || member.role !== "ADMIN") {
                    throw new Error("체험 관리자 로그인이 필요합니다.");
                }
                return member;
            },
            getStores: function () {
                return copy(storeData.stores);
            },
            saveCategory: function (id, values) {
                const name = values.name;
                const displayOrder = values.displayOrder;
                const visible = values.visible;
                repository.requireAdmin();
                const category = repository.getCategories().find(function (item) { return item.id === id; });
                const order = Number(displayOrder);
                if (!category || !name.trim() || name.trim().length > 100 || !Number.isInteger(order) || order < 1 || order > 999) {
                    throw new Error("분류명과 표시 순서를 확인해 주세요.");
                }
                const state = readState();
                const conflict = repository.getCategories().find(function (item) { return item.parentId === category.parentId && item.displayOrder === order && item.id !== id; });
                if (conflict) {
                    state.categoryOverrides[conflict.id] = Object.assign({}, state.categoryOverrides[conflict.id], { displayOrder: category.displayOrder });
                }
                state.categoryOverrides[id] = { name: name.trim(), displayOrder: order, visible: Boolean(visible) };
                writeState(state);
            },
            saveProduct: function (id, values) {
                repository.requireAdmin();
                const category = repository.getCategories().find(function (item) { return item.id === values.categoryId && item.isLeaf; });
                const price = Number(values.price);
                const stock = Number(values.onlineStock);
                if (!category || !values.name.trim() || values.name.trim().length > 150
                        || !Number.isInteger(price) || price < 1 || price > 10000000
                        || !Number.isInteger(stock) || stock < 0 || stock > 999999
                        || !["AVAILABLE", "SOLD_OUT", "SCHEDULED"].includes(values.status)
                        || !/^assets\/images\/mock-product-[0-5]\.svg$/.test(values.image)
                        || values.description.length > 2000 || values.brand.length > 100) {
                    throw new Error("상품명·분류·가격·재고·이미지 및 상태를 확인해 주세요.");
                }
                const changes = { name: values.name.trim(), categoryId: values.categoryId, price: price, onlineStock: stock, status: values.status, image: values.image, description: values.description, brand: values.brand };
                const state = readState();
                let productId = id;
                if (id) {
                    const original = repository.getProduct(id);
                    if (!original) {
                        throw new Error("상품을 찾을 수 없습니다.");
                    }
                    if (original.source.kind === "REFERENCE" && (original.name !== changes.name || original.price !== changes.price || original.categoryId !== changes.categoryId)) {
                        changes.source = { kind: "ADMIN_EDITED", reference: original.source.reference, note: "관찰 표본을 관리자에서 변경한 Mock 상품입니다. 현재 이름·가격·분류는 원본 관찰값이 아닙니다." };
                    }
                    state.productOverrides[id] = Object.assign({}, state.productOverrides[id], changes);
                } else {
                    productId = "CUSTOM-" + crypto.randomUUID();
                    state.createdProducts.push(Object.assign({ id: productId }, changes, { storeStocks: { seoul: 0, busan: 0, jeju: 0 }, rating: 0, reviewCount: 0, sales: 0, createdAt: new Date().toISOString(), source: { kind: "GENERATED", reference: null, note: "관리자에서 임의 생성한 Mock 상품" } }));
                }
                writeState(state);
                return repository.getProduct(productId);
            },
            updateInventory: function (productId, location, quantity) {
                repository.requireAdmin();
                const product = repository.getProduct(productId);
                const stock = Number(quantity);
                if (!product || !Number.isInteger(stock) || stock < 0 || stock > 999999
                        || !["online"].concat(storeData.stores.map(function (store) { return store.id; })).includes(location)) {
                    throw new Error("상품·재고 위치와 0 이상의 정수 수량을 확인해 주세요.");
                }
                const state = readState();
                const changes = location === "online" ? { onlineStock: stock } : { storeStocks: setStoreStock(product.storeStocks, location, stock) };
                state.productOverrides[productId] = Object.assign({}, state.productOverrides[productId], changes);
                writeState(state);
            },
			getAllOrders: function () {
			    return copy(readState().orders);
			},
            orderTransitions: function (status) {
                return { PAID: ["PREPARING", "CANCELLED"], PREPARING: ["SHIPPED", "CANCELLED"], SHIPPED: ["DELIVERED"], DELIVERED: [], CANCELLED: [], PAYMENT_FAILED: [] }[status] || [];
            },
            updateOrderStatus: function (id, status) {
                repository.requireAdmin();
                const state = readState();
                const order = state.orders.find(function (item) { return item.id === id; });
                if (!order || !repository.orderTransitions(order.status).includes(status)) {
                    throw new Error("허용되지 않는 주문 상태 변경입니다.");
                }
                if (status === "CANCELLED") {
                    order.items.forEach(function (item) {
                        const product = repository.getProduct(item.productId);
                        state.productOverrides[item.productId] = Object.assign({}, state.productOverrides[item.productId], { onlineStock: product.onlineStock + item.quantity });
                    });
                }
                order.status = status;
                writeState(state);
            },
            updateMemberStatus: function (id, status) {
                const admin = repository.requireAdmin();
                const member = repository.getMembers().find(function (item) { return item.id === id; });
                if (!member || !["ACTIVE", "SUSPENDED"].includes(status) || admin.id === id) {
                    throw new Error("회원과 상태를 확인해 주세요. 현재 관리자는 변경할 수 없습니다.");
                }
                const state = readState();
                state.members = state.members.filter(function (item) { return item.id !== id; });
                state.members.push(Object.assign({}, member, { status: status }));
                writeState(state);
            },
            reset: function () {
                localStorage.removeItem(storageKey);
                sessionStorage.removeItem("daisomall.mock.session");
                window.dispatchEvent(new Event("mock:changed"));
            },
        };
        window.Daiso.repository = repository;
        window.Daiso.state = { read: readState, write: writeState, copy: copy };
        return repository;
    });
})();
