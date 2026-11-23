package daiso.member.controller;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.InitialContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class SolapiSmsClient {

    private static final URI SEND_URL =
            URI.create("https://api.solapi.com/messages/v4/send-many/detail");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public void sendVerificationCode(String phone, String code) throws Exception {
        if (phone == null || !phone.matches("010[0-9]{8}")
                || code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("문자 수신 번호 또는 인증번호 형식이 올바르지 않습니다.");
        }

        String apiKey = InitialContext.doLookup("java:comp/env/solapiApiKey");
        String apiSecret = InitialContext.doLookup("java:comp/env/solapiApiSecret");
        String sender = InitialContext.doLookup("java:comp/env/solapiSenderNumber");

        String date = Instant.now().toString();
        String salt = UUID.randomUUID().toString();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8));
        String signature = String.format("%064x", new BigInteger(1, signed));

        String authorization = "HMAC-SHA256 apiKey=" + apiKey
                + ", date=" + date + ", salt=" + salt + ", signature=" + signature;

        JsonObject message = new JsonObject();
        message.addProperty("from", sender);
        message.addProperty("to", phone);
        message.addProperty("type", "SMS");
        message.addProperty("text", "[다이소몰] 인증번호는 " + code + "입니다.");

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(SEND_URL)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("솔라피 요청 실패 (HTTP " + response.statusCode() + ")");
        }

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject groupInfo = result.getAsJsonObject("groupInfo");
        JsonObject count = groupInfo == null ? null : groupInfo.getAsJsonObject("count");

        if (count == null || !count.has("registeredSuccess")
                || count.get("registeredSuccess").getAsInt() != 1) {
            throw new IOException("솔라피가 문자 발송을 접수하지 않았습니다.");
        }
    }
}