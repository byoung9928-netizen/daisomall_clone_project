package daiso.member.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.naming.InitialContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class NaverOAuthClient {

    private static final String REDIRECT_URI =
            "http://localhost:8080/daisomall/app/member/oauth/naver/callback";

    private static final String TOKEN_URL =
            "https://nid.naver.com/oauth2.0/token";

    private static final String USER_INFO_URL =
            "https://openapi.naver.com/v1/nid/me";

    public NaverProfile getProfile(String code, String state)
            throws Exception {

        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("네이버 인증 코드를 받지 못했습니다.");
        }

        String accessToken = requestAccessToken(code, state);
        JsonObject result = requestUserInfo(accessToken);

        JsonObject profile = result.getAsJsonObject("response");

        if (profile == null) {
            throw new IOException("네이버 회원 정보를 받지 못했습니다.");
        }

        return new NaverProfile(
                getString(profile, "id"),
                getString(profile, "name"),
                getString(profile, "email"),
                getString(profile, "mobile"),
                getString(profile, "gender"),
                getString(profile, "birthyear"),
                getString(profile, "birthday")
        );
    }
    
    public String getClientId() throws Exception {
        return getEnvironmentValue("naverClientId");
    }

    private String requestAccessToken(String code, String state)
            throws Exception {

        String clientId = getEnvironmentValue("naverClientId");
        String clientSecret = getEnvironmentValue("naverClientSecret");

        String formData =
                "grant_type=authorization_code"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + "&code=" + encode(code)
                + "&state=" + encode(state);

        JsonObject tokenResult = sendPostForm(TOKEN_URL, formData);
        String accessToken = getString(tokenResult, "access_token");

        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("네이버 액세스 토큰을 받지 못했습니다.");
        }

        return accessToken;
    }

    private JsonObject requestUserInfo(String accessToken)
            throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(USER_INFO_URL).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty(
                "Authorization",
                "Bearer " + accessToken
        );

        int statusCode = connection.getResponseCode();
        String responseBody = readResponse(connection, statusCode);
        connection.disconnect();

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException(
                    "네이버 회원 정보 요청 실패: HTTP " + statusCode
            );
        }

        return JsonParser.parseString(responseBody).getAsJsonObject();
    }

    private JsonObject sendPostForm(String address, String formData)
            throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded;charset=UTF-8"
        );

        try (OutputStream output = connection.getOutputStream()) {
            output.write(formData.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        String responseBody = readResponse(connection, statusCode);
        connection.disconnect();

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException(
                    "네이버 토큰 요청 실패: HTTP " + statusCode
                    + " / " + responseBody
            );
        }

        JsonObject result =
                JsonParser.parseString(responseBody).getAsJsonObject();

        if (result.has("error")) {
            throw new IOException(
                    "네이버 토큰 발급 실패: "
                    + getString(result, "error_description")
            );
        }

        return result;
    }

    private String readResponse(
            HttpURLConnection connection,
            int statusCode) throws IOException {

        InputStream input = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (input == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    private String getEnvironmentValue(String name) throws Exception {
        InitialContext context = new InitialContext();

        try {
            Object value = context.lookup("java:comp/env/" + name);

            if (value == null || value.toString().trim().isEmpty()) {
                throw new IllegalStateException(
                        name + " 설정을 찾을 수 없습니다."
                );
            }

            return value.toString().trim();
        } finally {
            context.close();
        }
    }

    private String getString(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return null;
        }

        JsonElement element = object.get(name);

        if (element == null || element.isJsonNull()) {
            return null;
        }

        return element.getAsString();
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(
                value == null ? "" : value,
                StandardCharsets.UTF_8.name()
        );
    }

    public static class NaverProfile {
        private final String id;
        private final String name;
        private final String email;
        private final String mobile;
        private final String gender;
        private final String birthYear;
        private final String birthday;

        public NaverProfile(
                String id,
                String name,
                String email,
                String mobile,
                String gender,
                String birthYear,
                String birthday) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.mobile = mobile;
            this.gender = gender;
            this.birthYear = birthYear;
            this.birthday = birthday;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getMobile() {
            return mobile;
        }

        public String getGender() {
            return gender;
        }

        public String getBirthYear() {
            return birthYear;
        }

        public String getBirthday() {
            return birthday;
        }
    }
}