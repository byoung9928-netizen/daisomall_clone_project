package daiso.order.delivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DeliveryTrackerClient {

    private final String clientId;
    private final String clientSecret;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public DeliveryTrackerClient() {
        clientId = System.getenv("DELIVERY_TRACKER_CLIENT_ID");
        clientSecret =
            System.getenv("DELIVERY_TRACKER_CLIENT_SECRET");

        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {

            throw new IllegalStateException(
                "배송조회 API 환경변수가 설정되지 않았습니다."
            );
        }
    }

    // 택배사 ID와 운송장 번호로 최근 배송정보 조회
    public JsonObject track(
            String carrierId, String trackingNumber)
            throws IOException, InterruptedException {

        if (carrierId == null || carrierId.isBlank()
                || carrierId.length() > 120) {
            throw new IllegalArgumentException(
                "택배사 ID를 확인해 주세요."
            );
        }

        if (trackingNumber == null || trackingNumber.isBlank()
                || trackingNumber.length() > 200) {
            throw new IllegalArgumentException(
                "운송장 번호를 확인해 주세요."
            );
        }

        // API에 요청할 조회 항목
        String query =
            "query Track($carrierId: ID!, "
            + "$trackingNumber: String!) { "
            + "track(carrierId: $carrierId, "
            + "trackingNumber: $trackingNumber) { "
            + "trackingNumber "
            + "lastEvent { "
            + "status { code name } "
            + "time description "
            + "} "
            + "} "
            + "}";

        // 검색값은 조회문에 직접 붙이지 않고 별도로 전달
        JsonObject variables = new JsonObject();
        variables.addProperty("carrierId", carrierId.trim());
        variables.addProperty(
            "trackingNumber", trackingNumber.trim()
        );

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        body.add("variables", variables);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://apis.tracker.delivery/graphql"
            ))
            .timeout(Duration.ofSeconds(15))
            .header(
                "Authorization",
                "TRACKQL-API-KEY "
                    + clientId.trim() + ":" + clientSecret.trim()
            )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                body.toString(), StandardCharsets.UTF_8
            ))
            .build();

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString(
                StandardCharsets.UTF_8
            )
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IOException(
                "배송조회 요청 실패: HTTP "
                    + response.statusCode()
            );
        }

        try {
            JsonObject result = JsonParser
                .parseString(response.body())
                .getAsJsonObject();

            // HTTP 성공이어도 API 응답 안에 오류가 있을 수 있음
            if (result.has("errors")
                    && !result.get("errors").isJsonNull()
                    && !result.getAsJsonArray("errors").isEmpty()) {

                throw new IOException(
                    "배송조회 API 오류입니다. "
                    + "인증정보와 운송장 번호를 확인해 주세요."
                );
            }

            JsonObject data = result.getAsJsonObject("data");

            if (data == null || !data.has("track")
                    || data.get("track").isJsonNull()) {
                throw new IOException(
                    "운송장 정보를 찾을 수 없습니다."
                );
            }

            JsonObject tracking = data.getAsJsonObject("track");

            if (!tracking.has("lastEvent")
                    || tracking.get("lastEvent").isJsonNull()) {
                throw new IOException(
                    "아직 배송 이력이 없습니다."
                );
            }

            return tracking;

        } catch (RuntimeException e) {
            throw new IOException(
                "배송조회 응답을 읽지 못했습니다.", e
            );
        }
    }
}