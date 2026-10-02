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
package cherry.mastersmith.useradmin.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * 利用者の管理の API（Intent 260930-user-admin の U3、契約 C3）を呼ぶテストの補助（実際の番号で待ち受けるアプリへ送る）。ヘッダーは
 * 名前と値の組（名前, 値, 名前, 値, ...）で足せる。
 */
public final class UserAdminApi {

    /** 利用者の管理の API の道。 */
    public static final String PATH = "/api/admin/users";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    /**
     * 作る。
     *
     * @param port アプリの待ち受けの番号
     */
    public UserAdminApi(int port) {
        this.client = new HttpTestClient(port);
    }

    /**
     * 問い合わせの値を URL の形にする（UTF-8）。
     *
     * @param value 値
     * @return URL の形にした値
     */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 一覧を読む。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param query 問い合わせ（例 {@code ?page=2}。無ければ空。値は呼び出し側で {@link #encode(String)} する）
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> list(String accessToken, String query, String... headers) {
        return client.send(builder(PATH + query, accessToken, headers).GET().build());
    }

    /**
     * 検索の文字を付けて一覧を読む（1ページ目）。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param q 検索の文字
     * @return 応答
     */
    public HttpResponse<String> search(String accessToken, String q) {
        return list(accessToken, "?q=" + encode(q));
    }

    /**
     * 氏名と言語を変える。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param userId 対象の利用者 ID（道に入れる文字列）
     * @param displayName 氏名
     * @param language 言語
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> putProfile(
            String accessToken, String userId, String displayName, String language, String... headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", displayName);
        body.put("language", language);
        return putProfileJson(accessToken, userId, MAPPER.writeValueAsString(body), headers);
    }

    /**
     * 本文をそのまま送って氏名と言語を変える。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param userId 対象の利用者 ID（道に入れる文字列）
     * @param json 本文
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> putProfileJson(String accessToken, String userId, String json, String... headers) {
        return client.send(builder(PATH + "/" + userId + "/profile", accessToken, headers)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    /**
     * 本文を JSON の文字列にする。
     *
     * @param body 本文
     * @return JSON の文字列
     */
    public static String json(Map<String, Object> body) {
        return MAPPER.writeValueAsString(body);
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
