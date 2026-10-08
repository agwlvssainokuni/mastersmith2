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
package cherry.mastersmith.group.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * グループの管理の API の要求を組み立てる手伝い（Intent 261004-role-menu の U3）。ID は文字列でも受け、整数でない値の確かめにも使う。
 * どの方法もスレッドから同時に呼んでよい。
 */
public final class GroupApi {

    /** グループの管理の API の道。 */
    public static final String PATH = "/api/admin/groups";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    /**
     * 作る。
     *
     * @param port アプリのポート
     */
    public GroupApi(int port) {
        this.client = new HttpTestClient(port);
    }

    /**
     * 本文の JSON を作る。
     *
     * @param body 項目
     * @return JSON
     */
    public static String json(Map<String, Object> body) {
        return MAPPER.writeValueAsString(body);
    }

    /**
     * 一覧を読む。
     *
     * @param token アクセストークン（無ければ null）
     * @param query 問い合わせ（{@code ?page=2} など。無ければ空）
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> list(String token, String query, String... headers) {
        return client.send(builder(PATH + query, token, headers).GET().build());
    }

    /**
     * 名前でグループを作る。
     *
     * @param token アクセストークン
     * @param name 名前
     * @return 応答
     */
    public HttpResponse<String> create(String token, String name) {
        return createJson(token, json(Map.of("name", name)));
    }

    /**
     * 本文の JSON でグループを作る。
     *
     * @param token アクセストークン
     * @param body 本文の JSON
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> createJson(String token, String body, String... headers) {
        return client.send(builder(PATH, token, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /**
     * 詳細を読む。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @return 応答
     */
    public HttpResponse<String> detail(String token, Object groupId) {
        return client.send(builder(PATH + "/" + groupId, token).GET().build());
    }

    /**
     * 名前を変える。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @param name 名前
     * @return 応答
     */
    public HttpResponse<String> rename(String token, Object groupId, String name) {
        return renameJson(token, groupId, json(Map.of("name", name)));
    }

    /**
     * 本文の JSON で名前を変える。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @param body 本文の JSON
     * @return 応答
     */
    public HttpResponse<String> renameJson(String token, Object groupId, String body) {
        return client.send(builder(PATH + "/" + groupId, token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /**
     * グループを消す。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @return 応答
     */
    public HttpResponse<String> delete(String token, Object groupId) {
        return client.send(builder(PATH + "/" + groupId, token).DELETE().build());
    }

    /**
     * メンバーを足す。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @return 応答
     */
    public HttpResponse<String> addMember(String token, Object groupId, long userId) {
        return addMemberJson(token, groupId, json(Map.of("userId", userId)));
    }

    /**
     * 本文の JSON でメンバーを足す。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @param body 本文の JSON
     * @return 応答
     */
    public HttpResponse<String> addMemberJson(String token, Object groupId, String body) {
        return client.send(builder(PATH + "/" + groupId + "/members", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /**
     * メンバーを外す。
     *
     * @param token アクセストークン
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @return 応答
     */
    public HttpResponse<String> removeMember(String token, Object groupId, Object userId) {
        return client.send(builder(PATH + "/" + groupId + "/members/" + userId, token)
                .DELETE()
                .build());
    }

    private HttpRequest.Builder builder(String path, String token, String... headers) {
        HttpRequest.Builder builder = client.request(path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return builder;
    }
}
