package daiso.member.controller;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.naming.InitialContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class KakaoOAuthClient {

    private static final String REDIRECT_URI =
            "http://localhost:8080/daisomall/app/member/oauth/kakao/callback";

    private static final String TOKEN_URL =
            "https://kauth.kakao.com/oauth/token";

    private static final String USER_INFO_URL =
            "https://kapi.kakao.com/v2/user/me";
    
    public static String redirectUri() {
        return System.getProperty("daisomall.kakao.redirectUri", REDIRECT_URI);
    }
    
    public String getRestApiKey() throws Exception {
        return getEnvironmentValue("kakaoRestApiKey");
    }
    
    public KakaoProfile getProfile(String authorizationCode)
            throws Exception {

        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "카카오 인증 코드를 받지 못했습니다."
            );
        }

        String accessToken = requestAccessToken(authorizationCode);
        JsonObject userInfo = requestUserInfo(accessToken);

        String kakaoId = getString(userInfo, "id");

        if (kakaoId == null || kakaoId.isEmpty()) {
            throw new IOException("카카오 회원 번호를 받지 못했습니다.");
        }

        String nickname = "카카오 회원";
        String email = null;

        if (userInfo.has("kakao_account")
                && userInfo.get("kakao_account").isJsonObject()) {

            JsonObject kakaoAccount =
                    userInfo.getAsJsonObject("kakao_account");

            if (kakaoAccount.has("profile")
                    && kakaoAccount.get("profile").isJsonObject()) {

                JsonObject profile =
                        kakaoAccount.getAsJsonObject("profile");

                String receivedNickname = getString(profile, "nickname");

                if (receivedNickname != null
                        && !receivedNickname.trim().isEmpty()) {
                    nickname = receivedNickname;
                }
            }

            String receivedEmail = getString(kakaoAccount, "email");
            boolean emailValid =
                    getBoolean(kakaoAccount, "is_email_valid");
            boolean emailVerified =
                    getBoolean(kakaoAccount, "is_email_verified");

            if (receivedEmail != null
                    && emailValid
                    && emailVerified) {
                email = receivedEmail.trim().toLowerCase(Locale.ROOT);
            }
        }

        return new KakaoProfile(kakaoId, nickname, email);
    }
    
    private boolean getBoolean(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return false;
        }

        JsonElement element = object.get(name);

        if (element == null || element.isJsonNull()) {
            return false;
        }

        return element.getAsBoolean();
    }

    private String requestAccessToken(String authorizationCode)
            throws Exception {

        String restApiKey = getEnvironmentValue("kakaoRestApiKey");
        String clientSecret = getEnvironmentValue("kakaoClientSecret");

        String formData =
                "grant_type=authorization_code"
                + "&client_id=" + encode(restApiKey)
                + "&redirect_uri=" + encode(redirectUri())
                + "&code=" + encode(authorizationCode)
                + "&client_secret=" + encode(clientSecret);

        JsonObject tokenResponse =
                sendPostForm(TOKEN_URL, formData);

        String accessToken = getString(tokenResponse, "access_token");

        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("카카오 액세스 토큰을 받지 못했습니다.");
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
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");

        int statusCode = connection.getResponseCode();
        String responseBody = readResponse(connection, statusCode);
        connection.disconnect();

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException(
                    "카카오 회원 정보 요청 실패: HTTP " + statusCode
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
                    "카카오 토큰 요청 실패: HTTP " + statusCode
                    + " / " + responseBody
            );
        }

        return JsonParser.parseString(responseBody).getAsJsonObject();
    }

    private String readResponse(
            HttpURLConnection connection,
            int statusCode) throws IOException {

        InputStream input;

        if (statusCode >= 200 && statusCode < 300) {
            input = connection.getInputStream();
        } else {
            input = connection.getErrorStream();
        }

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

    private String getEnvironmentValue(String name)
            throws Exception {

        InitialContext context = new InitialContext();

        Object value = context.lookup("java:comp/env/" + name);

        if (value == null || value.toString().trim().isEmpty()) {
            throw new IllegalStateException(
                    name + " 설정을 찾을 수 없습니다."
            );
        }

        return value.toString().trim();
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
        return URLEncoder.encode(value, "UTF-8");
    }

    public static class KakaoProfile {

        private final String id;
        private final String nickname;
        private final String email;

        public KakaoProfile(String id, String nickname, String email) {
            this.id = id;
            this.nickname = nickname;
            this.email = email;
        }

        public String getId() {
            return id;
        }

        public String getNickname() {
            return nickname;
        }

        public String getEmail() {
            return email;
        }
    }
}