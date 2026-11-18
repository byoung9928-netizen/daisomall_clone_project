package daiso.member.controller;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.naming.InitialContext;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GoogleOAuthClient {

    private static final String REDIRECT_URI =
            "http://localhost:8080/daisomall/app/member/oauth/google/callback";

    public static String getClientId() throws Exception {
        return getEnvironmentValue("googleClientId");
    }

    public static GoogleProfile getProfile(String code) throws Exception {
        String clientId = getEnvironmentValue("googleClientId");
        String clientSecret = getEnvironmentValue("googleClientSecret");

        String formData =
                "code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&grant_type=authorization_code";

        String tokenResponse = sendPost(
                "https://oauth2.googleapis.com/token",
                formData
        );

        JsonObject tokenJson = JsonParser.parseString(tokenResponse)
                .getAsJsonObject();

        if (!tokenJson.has("access_token")) {
            throw new Exception("Google 액세스 토큰을 받지 못했습니다.");
        }

        String accessToken = tokenJson.get("access_token").getAsString();

        String profileResponse = sendGet(
                "https://openidconnect.googleapis.com/v1/userinfo",
                accessToken
        );

        JsonObject profileJson = JsonParser.parseString(profileResponse)
                .getAsJsonObject();

        String id = getJsonString(profileJson, "sub");
        String email = getJsonString(profileJson, "email");
        String name = getJsonString(profileJson, "name");

        boolean emailVerified =
                profileJson.has("email_verified")
                && profileJson.get("email_verified").getAsBoolean();

        if (id.isEmpty()) {
            throw new Exception("Google 사용자 고유 ID를 받지 못했습니다.");
        }

        if (email.isEmpty() || !emailVerified) {
            throw new Exception("Google에서 인증된 이메일을 확인하지 못했습니다.");
        }

        return new GoogleProfile(id, email, name);
    }

    private static String getEnvironmentValue(String name) throws Exception {
        InitialContext context = new InitialContext();

        try {
            Object value = context.lookup("java:comp/env/" + name);
            return String.valueOf(value);
        } finally {
            context.close();
        }
    }

    private static String sendPost(String urlText, String formData)
            throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
            );

            byte[] body = formData.getBytes(StandardCharsets.UTF_8);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            return readResponse(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String sendGet(String urlText, String accessToken)
            throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + accessToken
            );

            return readResponse(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection connection)
            throws Exception {
        int statusCode = connection.getResponseCode();

        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            throw new Exception("Google 서버 응답이 비어 있습니다. 상태 코드: "
                    + statusCode);
        }

        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new Exception("Google 요청 실패. 상태 코드: "
                    + statusCode + ", 응답: " + response);
        }

        return response.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String getJsonString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }

        return json.get(key).getAsString();
    }

    public static class GoogleProfile {
        private final String id;
        private final String email;
        private final String name;

        public GoogleProfile(String id, String email, String name) {
            this.id = id;
            this.email = email;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}