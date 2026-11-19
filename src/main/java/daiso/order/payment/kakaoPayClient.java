package daiso.order.payment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class kakaoPayClient {
	public static final String CID = "TC0ONETIME";
	
	private final String secretKey;
	
	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	
	public kakaoPayClient() {
		secretKey = System.getenv("KAKAOPAY_SECRET_KEY");
		
		if(secretKey == null || secretKey.isBlank()) {
			throw new IllegalStateException(
					"카카오페이 테스트키가 설정되지 않았습니다."
					);
		}
	}
	public String request(String action, String json)
			throws IOException, InterruptedException{
		if(!"ready".equals(action)
				&&!"approve".equals(action)
				&&!"cancel".equals(action)
				&&!"order".equals(action)) {
			throw new IllegalArgumentException(
					"지원하지 않는 결제 요청입니다."
					);
		}
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(
						"https://open-api.kakaopay.com/online/v1/payment/"
						+action
						))
				.timeout(Duration.ofSeconds(30))
				.header("authorization","SECRET_KEY "+secretKey)
				.header("Content-Type","application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString()
				);
		
		if (response.statusCode() < 200
		        || response.statusCode() >= 300) {

		    JsonObject error =
		        JsonParser.parseString(response.body()).getAsJsonObject();

		    String code = error.has("error_code")
		        ? error.get("error_code").getAsString() : "없음";

		    String message = error.has("error_message")
		        ? error.get("error_message").getAsString() : "없음";

		    throw new IOException(
		        "HTTP " + response.statusCode()
		        + " / 코드: " + code
		        + " / 사유: " + message
		    );
		}
		return response.body();
	}
	public JsonObject ready(
			String orderNo,
			String memberId,
			String itemName,
			int quantity,
			int totalAmount,
			String approvalUrl,
			String cancelUrl,
			String failUrl)
			throws IOException, InterruptedException{
		
		if(quantity <1||totalAmount <1) {
			throw new IllegalArgumentException(
				"주문 수량과 결제 금액을 확인해 주세요."
					);
			}
		JsonObject data=new JsonObject();
		data.addProperty("cid", CID);
		data.addProperty("partner_order_id", orderNo);
	    data.addProperty("partner_user_id", memberId);
	    data.addProperty("item_name", itemName);
	    data.addProperty("quantity", quantity);
	    data.addProperty("total_amount", totalAmount);
	    data.addProperty("tax_free_amount", 0);
	    data.addProperty("approval_url", approvalUrl);
	    data.addProperty("cancel_url", cancelUrl);
	    data.addProperty("fail_url", failUrl);
	    
	    String response = request("ready", data.toString());

	    return JsonParser.parseString(response).getAsJsonObject();
	}
		
	public JsonObject approve(
			String tid,//결제준비 시 카카오페이가 발급한 결제 번호
			String orderNo,//우리가 만든 주문 번호
			String memberId,//주문 회원 번호
			String pgToken)//인증후 돌아오는 주소로 전달받는 토큰
			throws IOException, InterruptedException{
		JsonObject data = new JsonObject();
		
		data.addProperty("cid", CID);
	    data.addProperty("tid", tid);
	    data.addProperty("partner_order_id", orderNo);
	    data.addProperty("partner_user_id", memberId);
	    data.addProperty("pg_token", pgToken);

	    String response = request("approve", data.toString());

	    return JsonParser.parseString(response).getAsJsonObject();
	}
	
	// 결제 번호로 카카오페이의 실제 결제 상태 조회
	public JsonObject getPayment(String tid)
	        throws IOException, InterruptedException {

	    if (tid == null || tid.isBlank()) {
	        throw new IllegalArgumentException(
	            "조회할 결제 번호가 없습니다."
	        );
	    }

	    JsonObject data = new JsonObject();

	    data.addProperty("cid", CID);
	    data.addProperty("tid", tid);

	    String response = request("order", data.toString());

	    return JsonParser.parseString(response).getAsJsonObject();
	}
	// 승인된 결제의 전체 또는 일부 금액 취소 요청
	public JsonObject cancelPayment(String tid, int cancelAmount)
	        throws IOException, InterruptedException {

	    if (tid == null || tid.isBlank()) {
	        throw new IllegalArgumentException(
	            "취소할 결제 번호가 없습니다."
	        );
	    }

	    if (cancelAmount <= 0) {
	        throw new IllegalArgumentException(
	            "취소 금액을 확인해 주세요."
	        );
	    }

	    JsonObject data = new JsonObject();

	    data.addProperty("cid", CID);
	    data.addProperty("tid", tid);
	    data.addProperty("cancel_amount", cancelAmount);

	    // 기존 결제 준비 때 비과세 금액을 0으로 설정했음
	    data.addProperty("cancel_tax_free_amount", 0);

	    String response = request("cancel", data.toString());

	    return JsonParser.parseString(response).getAsJsonObject();
	}
}
