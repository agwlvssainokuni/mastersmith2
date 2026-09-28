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
package cherry.mastersmith.invitation.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * 招待の管理の API（契約 C5）と登録の完了の API（契約 C6）を呼ぶテストの補助（実際の番号で待ち受けるアプリへ送る）。ヘッダーは名前と
 * 値の組（名前, 値, 名前, 値, ...）で足せる。
 */
public final class InvitationApi {

    /** 招待の管理の API の道。 */
    public static final String ADMIN = "/api/admin/invitations";

    /** リンクの確かめの道。 */
    public static final String VERIFY = "/api/registration/verify";

    /** 登録の完了の道。 */
    public static final String COMPLETE = "/api/registration/complete";

    /** 登録の完了で使うテストのパスワード（明らかにテスト用と分かる値）。 */
    public static final String PASSWORD = "登録用のテストパスワード-01";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    /**
     * 作る。
     *
     * @param port アプリの待ち受けの番号
     */
    public InvitationApi(int port) {
        this.client = new HttpTestClient(port);
    }

    /**
     * 招待する。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param email 招待先
     * @param language 言語
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> invite(String accessToken, String email, String language, String... headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("language", language);
        return post(ADMIN, accessToken, MAPPER.writeValueAsString(body), headers);
    }

    /**
     * 一覧を読む。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param query 問い合わせ（例 {@code ?page=2}。無ければ空）
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> list(String accessToken, String query, String... headers) {
        return client.send(builder(ADMIN + query, accessToken, headers).GET().build());
    }

    /**
     * 送り直す。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param invitationId 招待の ID
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> resend(String accessToken, long invitationId, String... headers) {
        return post(ADMIN + "/" + invitationId + "/resend", accessToken, null, headers);
    }

    /**
     * 取り消す。
     *
     * @param accessToken アクセストークン（無ければ null）
     * @param invitationId 招待の ID
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> cancel(String accessToken, long invitationId, String... headers) {
        return post(ADMIN + "/" + invitationId + "/cancel", accessToken, null, headers);
    }

    /**
     * リンクを確かめる（ログインなし）。
     *
     * @param token トークン
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> verify(String token, String... headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        return post(VERIFY, null, MAPPER.writeValueAsString(body), headers);
    }

    /**
     * 登録を完了する（ログインなし。表示の設定は ja・system・md、パスワードは {@link #PASSWORD}）。
     *
     * @param token トークン
     * @param displayName 氏名
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> complete(String token, String displayName, String... headers) {
        return completeWith(token, displayName, PASSWORD, PASSWORD, "ja", headers);
    }

    /**
     * 登録を完了する（ログインなし）。
     *
     * @param token トークン
     * @param displayName 氏名
     * @param password パスワード
     * @param confirmation 確かめ
     * @param language 言語
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> completeWith(
            String token,
            String displayName,
            String password,
            String confirmation,
            String language,
            String... headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("displayName", displayName);
        body.put("password", password);
        body.put("passwordConfirmation", confirmation);
        body.put("language", language);
        body.put("theme", "system");
        body.put("fontSize", "md");
        return post(COMPLETE, null, MAPPER.writeValueAsString(body), headers);
    }

    /**
     * 本文をそのまま POST する。
     *
     * @param path 道
     * @param accessToken アクセストークン（無ければ null）
     * @param json 本文（無ければ null）
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> post(String path, String accessToken, String json, String... headers) {
        HttpRequest.Builder builder = builder(path, accessToken, headers);
        if (json == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        }
        return client.send(builder.build());
    }

    /**
     * GET を送る。
     *
     * @param path 道
     * @param accessToken アクセストークン（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> get(String path, String accessToken) {
        return client.send(builder(path, accessToken).GET().build());
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
