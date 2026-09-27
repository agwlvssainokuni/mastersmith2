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
package cherry.mastersmith.user.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * ログインした利用者の自分の設定の API（契約 C4、{@code /api/me/}）を呼ぶテストの補助（実際の番号で待ち受けるアプリへ送る）。
 *
 * <p>ヘッダーは名前と値の組（名前, 値, 名前, 値, ...）で足せる（例: {@code Accept-Language}・{@code User-Agent}）。
 */
public final class MeApi {

    /** プリファレンスのパス。 */
    public static final String PREFERENCES = "/api/me/preferences";

    /** パスワードの変更のパス。 */
    public static final String PASSWORD = "/api/me/password";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    /**
     * 作る。
     *
     * @param port アプリの待ち受けの番号
     */
    public MeApi(int port) {
        this.client = new HttpTestClient(port);
    }

    /**
     * プリファレンスを読む。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> getPreferences(String accessToken, String... headers) {
        return client.send(builder(PREFERENCES, accessToken, headers).GET().build());
    }

    /**
     * プリファレンスを保存する。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param displayName 氏名
     * @param language 言語
     * @param theme テーマ
     * @param fontSize 文字の大きさ
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> putPreferences(
            String accessToken, String displayName, String language, String theme, String fontSize, String... headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", displayName);
        body.put("language", language);
        body.put("theme", theme);
        body.put("fontSize", fontSize);
        return putPreferencesRaw(accessToken, MAPPER.writeValueAsString(body), headers);
    }

    /**
     * プリファレンスの保存に、本文をそのまま送る。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param json 本文
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> putPreferencesRaw(String accessToken, String json, String... headers) {
        return client.send(builder(PREFERENCES, accessToken, headers)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    /**
     * パスワードを変える。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param currentPassword 今のパスワード
     * @param newPassword 新しいパスワード
     * @param confirmation 確かめの値
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> changePassword(
            String accessToken, String currentPassword, String newPassword, String confirmation, String... headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        body.put("newPasswordConfirmation", confirmation);
        return client.send(builder(PASSWORD, accessToken, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build());
    }

    private HttpRequest.Builder builder(String path, String accessToken, String... headers) {
        HttpRequest.Builder builder = client.request(path);
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return builder;
    }
}
