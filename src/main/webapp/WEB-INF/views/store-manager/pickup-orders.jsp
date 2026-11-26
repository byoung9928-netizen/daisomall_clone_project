<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map" %>
<%@ page import="daiso.product.controller.ProductView" %>
<%
    List<Map<String, Object>> orders =
            (List<Map<String, Object>>) request.getAttribute("pickupOrders");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">
    <main class="admin-content">
        <h1 class="page-title">
            <%= ProductView.escape(request.getAttribute("storeManagerName")) %> 픽업 주문
        </h1>

        <nav aria-label="매장 관리자 메뉴">
            <a class="button secondary"
               href="${pageContext.request.contextPath}/app/store-manager/inventory">매장 재고</a>
            <a class="button secondary" aria-current="page"
               href="${pageContext.request.contextPath}/app/store-manager/pickup-orders">픽업 주문</a>
        </nav>

        <p>최근 결제 완료 주문 · 최대 50건</p>
        <div class="admin-table-scroll">
            <button type="button" id="startPickupScan">픽업 QR 스캔</button>
<button type="button" id="stopPickupScan" hidden>카메라 닫기</button>
<video id="pickupCamera" playsinline muted hidden width="320"></video>
<p id="pickupScanMessage" role="status"></p>

		<dialog id="pickupApproval">
		    <form id="pickupApprovalForm" method="post"
		          action="${pageContext.request.contextPath}/app/store-manager/pickup-orders/status"
		          data-scan-url="${pageContext.request.contextPath}/app/store-manager/pickup-orders/scan">
		        <h2>픽업 수령 승인</h2>
		        <p id="pickupApprovalOrder"></p>
		        <input type="hidden" name="csrf"
		               value="<%= ProductView.escape(request.getAttribute("storeManagerCsrf")) %>">
		        <input type="hidden" name="orderNo">
		        <input type="hidden" name="pickupToken">
		        <input type="hidden" name="action" value="complete">
		
		        <label>
		            매장 관리자 비밀번호
		            <input type="password" name="approvalPassword"
		                   autocomplete="current-password" required>
		        </label>
		        <button type="button" id="cancelPickupApproval">취소</button>
		        <button type="submit">수령 완료</button>
		    </form>
		</dialog>
            <table class="admin-table">
                <thead>
                    <tr>
                        <th scope="col">주문번호</th>
                        <th scope="col">수령인</th>
                        <th scope="col">준비할 상품</th>
                        <th scope="col">결제 일시</th>
                        <th scope="col">결제 상태</th>
                        <th scope="col">픽업 진행 상태</th>
                    </tr>
                </thead>
                <tbody>
                    <% if (orders.isEmpty()) { %>
                        <tr><td colspan="6">결제 완료된 픽업 주문이 없습니다.</td></tr>
                    <% } else {
                        for (Map<String, Object> order : orders) { %>
                        <tr>
                            <td><%= ProductView.escape(order.get("orderNo")) %></td>
                            <td><%= ProductView.escape(order.get("recipient")) %></td>

<td>
    <% List<?> items = (List<?>) order.get("items");
       if (items == null || items.isEmpty()) { %>
        준비할 상품 정보가 없습니다.
    <% } else { %>
        <ul>
            <% for (Object item : items) { %>
                <li><%= ProductView.escape(item) %></li>
            <% } %>
        </ul>
    <% } %>
</td>

<td><%= ProductView.escape(order.get("approvedAt")) %></td>
                            <td>결제 완료</td>
						<td>
    <% String pickupStatus = (String) order.get("pickupStatus"); %>
    <%= "PICKED_UP".equals(pickupStatus) ? "수령 완료"
            : "READY".equals(pickupStatus) ? "수령 대기" : "준비 대기" %>

    <% if (pickupStatus == null || "READY".equals(pickupStatus)) { %>
        <form method="post"
              action="${pageContext.request.contextPath}/app/store-manager/pickup-orders/status">
            <input type="hidden" name="csrf"
                   value="<%= ProductView.escape(request.getAttribute("storeManagerCsrf")) %>">
            <input type="hidden" name="orderNo"
                   value="<%= ProductView.escape(order.get("orderNo")) %>">
                   <% if ("READY".equals(pickupStatus)) { %>
		    <label>
		        픽업 QR 토큰
		        <input type="text" name="pickupToken"
		               minlength="43" maxlength="43"
		               pattern="[A-Za-z0-9_\-]{43}"
		               autocomplete="off" spellcheck="false" required>
		    </label>
		
		    <label>
		        매장 관리자 승인 비밀번호
		        <input type="password" name="approvalPassword"
		               autocomplete="current-password" required>
		    </label>
					<% } %>
            <button type="submit" class="button secondary" name="action"
                    value="<%= pickupStatus == null ? "ready" : "complete" %>">
                <%= pickupStatus == null ? "준비 완료" : "수령 완료" %>
            </button>
        </form>
    <% } %>
</td>
					</tr>
                    <%  }
                    } %>
                </tbody>
            </table>
        </div>
    </main>
    <script src="https://cdn.jsdelivr.net/npm/@zxing/browser@0.1.5/umd/zxing-browser.min.js"></script>
<script>
    const startButton = document.getElementById("startPickupScan");
    const stopButton = document.getElementById("stopPickupScan");
    const camera = document.getElementById("pickupCamera");
    const message = document.getElementById("pickupScanMessage");
    const approval = document.getElementById("pickupApproval");
    const form = document.getElementById("pickupApprovalForm");

    let scanning = false;
    let controls;

    function stopCamera() {
        scanning = false;
        controls?.stop();
        controls = undefined;
        camera.hidden = true;
        stopButton.hidden = true;
        startButton.disabled = false;
    }

    async function openApproval(pickupToken) {
        message.textContent = "픽업 주문을 확인하는 중입니다.";
        startButton.disabled = true;
        try {
            const response = await fetch(form.dataset.scanUrl, {
                method: "POST",
                body: new URLSearchParams({
                    csrf: form.elements.namedItem("csrf").value,
                    pickupToken
                })
            });

            const result = await response.text();
            if (!response.ok) {
                message.textContent = result;
                return;
            }

            form.elements.namedItem("orderNo").value = result.trim();
            form.elements.namedItem("pickupToken").value = pickupToken;
            document.getElementById("pickupApprovalOrder").textContent =
                "주문번호 " + result.trim();
            message.textContent = "";
            approval.showModal();
            form.elements.namedItem("approvalPassword").focus();
        } catch {
            message.textContent = "주문 확인에 실패했습니다. 다시 스캔해 주세요.";
        } finally {
            startButton.disabled = false;
        }
    }

    startButton.addEventListener("click", async () => {
        scanning = true;
        startButton.disabled = true;
        camera.hidden = false;
        message.textContent = "";

        try {
            const reader = new ZXingBrowser.BrowserQRCodeReader();
            const activeControls = await reader.decodeFromVideoDevice(
                undefined, camera, (result, error, scannerControls) => {
                    if (!result || !scanning) return;
                    controls = scannerControls;
                    stopCamera();
                    void openApproval(result.getText().trim());
                });

            if (!scanning) {
                activeControls.stop();
                return;
            }
            controls = activeControls;
            stopButton.hidden = false;
        } catch {
            stopCamera();
            message.textContent = "카메라를 열지 못했습니다. 카메라 권한을 확인해 주세요.";
        }
    });

    stopButton.addEventListener("click", stopCamera);
    document.getElementById("cancelPickupApproval")
        .addEventListener("click", () => approval.close());
    approval.addEventListener("close", () => form.reset());
</script>
</body>
</html>