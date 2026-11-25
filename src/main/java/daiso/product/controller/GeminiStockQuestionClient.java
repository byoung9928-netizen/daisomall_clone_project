package daiso.product.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.naming.InitialContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class GeminiStockQuestionClient {

    private static final URI API_URL = URI.create(
            "https://generativelanguage.googleapis.com/v1beta/models/"
            + "gemini-3.5-flash-lite:generateContent");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public JsonObject parseQuestion(String question, String history) throws Exception {
        if (question == null || question.isBlank() || question.length() > 200) {
            throw new IllegalArgumentException("질문을 1~200자로 입력해 주세요.");
        }

        String apiKey = InitialContext.doLookup("java:comp/env/geminiApiKey");

        String instructions =
                "한국어로 대화하는 AI 어시스턴트다. 일상, 공부, 코딩 질문에도 답한다. "
                + "특정 매장의 상품 재고를 확인하려는 경우에만 kind를 stock으로 설정한다. "
                + "stock이면 store에 매장명이나 지역, product에 상품명이나 품번을 넣는다. "
                + "빠진 검색어와 answer는 빈 문자열로 둔다. "
                + "우리 쇼핑몰의 상품 가격을 묻는 질문은 kind를 product로 설정한다. "
                + "product에는 상품명이나 품번을 넣고 store와 answer는 비운다. "
                + "가격 질문에 상품명이 빠졌으면 product는 빈 문자열로 둔다. "
                + "특정 매장을 지정하지 않고 상품을 살 수 있는 매장이나 픽업 가능 매장을 묻는 질문은 "
                + "kind를 pickup_stores로 설정한다. product에 상품명이나 품번을 넣고 store와 answer는 비운다. "
                + "특정 매장의 재고를 묻는 질문은 기존처럼 kind를 stock으로 설정한다. "
                + "택배 배송비, 무료배송 기준, 매장픽업 수수료를 묻는 질문은 kind를 shipping_policy로 설정한다. "
                + "반품이나 환불의 신청 방법 또는 신청 조건을 묻는 질문은 kind를 return_policy로 설정한다. "
                + "shipping_policy와 return_policy에는 store, product, answer를 빈 문자열로 둔다. "
                + "본인의 주문, 배송, 반품, 환불 상태를 묻는 경우는 general로 분류하고, 현재 챗봇에서 내 주문 상태를 조회할 수 없다고 안내한다. "
                + "다른 질문은 kind를 general로 하고 answer에 답변을 넣으며 store와 product는 비운다. "
                + "실제 재고, 주문 상태, 배송 상태, 가격은 추측하지 않는다. "
                + "답변은 읽기 쉬운 일반 텍스트로 작성한다.";

        String input = history == null || history.isBlank()
                ? question.strip()
                : "직전 대화:\n" + history + "\n\n새 질문:\n" + question.strip();

        JsonObject body = new JsonObject();
        body.add("systemInstruction", textContent(instructions));
        JsonArray contents = new JsonArray();
        contents.add(textContent(input));
        body.add("contents", contents);

        JsonObject schema = JsonParser.parseString(
                "{\"type\":\"object\",\"properties\":{"
                + "\"kind\":{\"type\":\"string\",\"enum\":[\"general\",\"stock\",\"product\","
                + "\"pickup_stores\",\"shipping_policy\",\"return_policy\"]},"
                + "\"store\":{\"type\":\"string\"},\"product\":{\"type\":\"string\"},"
                + "\"answer\":{\"type\":\"string\"}},"
                + "\"required\":[\"kind\",\"store\",\"product\",\"answer\"],"
                + "\"additionalProperties\":false}").getAsJsonObject();

        JsonObject config = new JsonObject();
        config.addProperty("responseMimeType", "application/json");
        config.addProperty("maxOutputTokens", 1500);
        config.add("responseJsonSchema", schema);
        body.add("generationConfig", config);

        HttpRequest request = HttpRequest.newBuilder(API_URL)
                .timeout(Duration.ofSeconds(25))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini 요청 실패 (HTTP " + response.statusCode() + ")");
        }

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = result.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0) {
            throw new IOException("Gemini 응답에 답변이 없습니다.");
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        if (!candidate.has("finishReason")
                || !"STOP".equals(candidate.get("finishReason").getAsString())) {
            throw new IOException("Gemini 답변 생성이 완료되지 않았습니다.");
        }

        JsonObject content = candidate.getAsJsonObject("content");
        JsonArray parts = content == null ? null : content.getAsJsonArray("parts");
        StringBuilder answer = new StringBuilder();

        if (parts != null) {
            for (JsonElement element : parts) {
                JsonObject part = element.getAsJsonObject();
                if (part.has("thought") && part.get("thought").getAsBoolean()) {
                    continue;
                }
                if (part.has("text")) {
                    answer.append(part.get("text").getAsString());
                }
            }
        }

        if (answer.length() == 0) {
            throw new IOException("Gemini 답변이 비어 있습니다.");
        }
        return JsonParser.parseString(answer.toString()).getAsJsonObject();
    }

    private static JsonObject textContent(String value) {
        JsonObject part = new JsonObject();
        part.addProperty("text", value);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.add("parts", parts);
        return content;
    }
}