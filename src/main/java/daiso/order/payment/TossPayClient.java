package daiso.order.payment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TossPayClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ===== 토스페이 테스트 결제 승인 =====
    public JsonObject confirm(String paymentKey, String orderId, int amount)
            throws IOException, InterruptedException {

        String secretKey = System.getenv("TOSS_PAYMENTS_SECRET_KEY");
        if (secretKey == null || !secretKey.startsWith("test_sk")) {
            throw new IllegalStateException("토스페이 테스트 시크릿 키를 확인해 주세요.");
        }

        String authorization = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8));

        JsonObject body = new JsonObject();
        body.addProperty("paymentKey", paymentKey);
        body.addProperty("orderId", orderId);
        body.addProperty("amount", amount);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tosspayments.com/v1/payments/confirm"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + authorization)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("토스페이 승인 응답 오류 (HTTP " + response.statusCode() + ")");
        }

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

  public JsonObject cancel(String paymentKey, int amount, String reason, String idempotencyKey ) 
	  throws IOException, InterruptedException{
	  
	  String secretKey = System.getenv("TOSS_PAYMENTS_SECRET_KEY");
	  if (secretKey ==null || !secretKey.startsWith("test_sk")) {
		  throw new IllegalStateException("토스페이 테스트 시크릿 키를 확인해 주세요.");
	  }
	  String authorization=Base64.getEncoder().encodeToString(
			  (secretKey+":").getBytes(StandardCharsets.UTF_8));
	  
	  JsonObject body= new JsonObject();
	  body.addProperty("cancelReason", reason);
	  body.addProperty("cancelAmount", amount);
	  
	  String encodedPaymentKey=java.net.URLEncoder
			  .encode(paymentKey, StandardCharsets.UTF_8)
			  .replace("+","%20" );
	  
	  HttpRequest request = HttpRequest.newBuilder()
			  .uri(URI.create("https://api.tosspayments.com/v1/payments/"
					  +encodedPaymentKey+"/cancel"))
			  .timeout(Duration.ofSeconds(15))
			  .header("Authorization","Basic " +authorization)
			  .header("Content-Type","application/json")
			  .header("Idempotency-Key", idempotencyKey)
			  .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			  .build();
	  
	  HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	  
	  if(response.statusCode()!=200) {
		  throw new IOException("토스페이 취소 응답 오류(HTTP "+response.statusCode()+")");
		  
	  }
	  return JsonParser.parseString(response.body()).getAsJsonObject();
  }
  
}