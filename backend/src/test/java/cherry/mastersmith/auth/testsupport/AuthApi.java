/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.auth.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/** 認証の API を呼ぶテストの補助（実際の番号で待ち受けるアプリへ送る）。 */
public final class AuthApi {

    /** リフレッシュトークンの Cookie の名前。 */
    public static final String COOKIE = "mastersmith_refresh";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    private final int port;

    /**
     * 作る。
     *
     * @param port アプリの待ち受けの番号
     */
    public AuthApi(int port) {
        this.client = new HttpTestClient(port);
        this.port = port;
    }

    /**
     * 自分の配信元（Origin に入れる値）を返す。
     *
     * @return 配信元
     */
    public String origin() {
        return "http://localhost:" + port;
    }

    /**
     * ログインする。
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return 応答
     */
    public HttpResponse<String> login(String email, String password) {
        String body = MAPPER.writeValueAsString(java.util.Map.of("email", email, "password", password));
        return client.send(client.request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /**
     * 更新する。
     *
     * @param cookie Cookie の値（無ければ null）
     * @param origin Origin の値（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> refresh(String cookie, String origin) {
        return post("/api/auth/session/refresh", cookie, origin);
    }

    /**
     * ログアウトする。
     *
     * @param cookie Cookie の値（無ければ null）
     * @param origin Origin の値（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> logout(String cookie, String origin) {
        return post("/api/auth/session/logout", cookie, origin);
    }

    /**
     * 保護されたテスト用の窓口を呼ぶ。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> me(String accessToken) {
        HttpRequest.Builder builder = client.request(ProtectedTestEndpoint.PATH).GET();
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(builder.build());
    }

    private HttpResponse<String> post(String path, String cookie, String origin) {
        HttpRequest.Builder builder = client.request(path).POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", COOKIE + "=" + cookie);
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return client.send(builder.build());
    }

    /**
     * 応答の Set-Cookie のうち、リフレッシュトークンの Cookie の行を返す。
     *
     * @param response 応答
     * @return Set-Cookie の行（無ければ空）
     */
    public static Optional<String> setCookie(HttpResponse<String> response) {
        List<String> values = response.headers().allValues("Set-Cookie");
        return values.stream().filter(value -> value.startsWith(COOKIE + "=")).findFirst();
    }

    /**
     * 応答の Set-Cookie から、リフレッシュトークンの値を取り出す。
     *
     * @param response 応答
     * @return 値
     */
    public static String cookieValue(HttpResponse<String> response) {
        String line = setCookie(response).orElseThrow();
        return line.substring(COOKIE.length() + 1, line.indexOf(';'));
    }

    /**
     * 応答の本文からアクセストークンを取り出す。
     *
     * @param response 応答
     * @return アクセストークン
     */
    public static String accessToken(HttpResponse<String> response) {
        return (String) HttpTestClient.json(response).get("accessToken");
    }
}
