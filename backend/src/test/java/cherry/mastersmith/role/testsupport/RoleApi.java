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
package cherry.mastersmith.role.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * ロールの管理と権限の設定の API の要求を組み立てる手伝い（Intent 261004-role-menu の U4、計画の 7.3）。ID は文字列でも受け、整数でない
 * 値の確かめにも使う。木の名前は問い合わせの引数としてエンコードする。どの方法もスレッドから同時に呼んでよい。
 */
public final class RoleApi {

    /** ロールの管理の API の道。 */
    public static final String PATH = "/api/admin/roles";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpTestClient client;

    /**
     * 作る。
     *
     * @param port アプリのポート
     */
    public RoleApi(int port) {
        this.client = new HttpTestClient(port);
    }

    /**
     * 本文の JSON を作る。
     *
     * @param body 項目
     * @return JSON
     */
    public static String json(Object body) {
        return MAPPER.writeValueAsString(body);
    }

    /**
     * 権限の保存の1件を作る（null の項目も載せる）。
     *
     * @param schema スキーマの名前
     * @param table テーブルの名前（無ければ null）
     * @param column カラムの名前（無ければ null）
     * @param main 主権限（無ければ null）
     * @param create CREATE（無ければ null）
     * @param delete DELETE（無ければ null）
     * @return 1件
     */
    public static Map<String, Object> entry(
            String schema, String table, String column, String main, Boolean create, Boolean delete) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("schemaName", schema);
        entry.put("tableName", table);
        entry.put("columnName", column);
        entry.put("main", main);
        entry.put("create", create);
        entry.put("delete", delete);
        return entry;
    }

    /**
     * 権限の保存の本文を作る。
     *
     * @param schema 範囲のスキーマ
     * @param table 範囲のテーブル（スキーマの範囲は null）
     * @param entries 対象ごとの値
     * @return 本文の JSON
     */
    public static String saveBody(String schema, String table, List<Map<String, Object>> entries) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("schemaName", schema);
        scope.put("tableName", table);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", scope);
        body.put("entries", entries);
        return json(body);
    }

    /**
     * 一覧を読む。
     *
     * @param token アクセストークン（無ければ null）
     * @param query 問い合わせ（{@code ?page=2} など。無ければ空）
     * @return 応答
     */
    public HttpResponse<String> list(String token, String query) {
        return send("GET", PATH + query, token, null);
    }

    /**
     * 名前でロールを作る。
     *
     * @param token アクセストークン
     * @param name 名前
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> create(String token, String name, String... headers) {
        return createJson(token, json(Map.of("name", name)), headers);
    }

    /**
     * 本文の JSON でロールを作る。
     *
     * @param token アクセストークン
     * @param body 本文の JSON
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> createJson(String token, String body, String... headers) {
        return send("POST", PATH, token, body, headers);
    }

    /**
     * ロール1件を読む。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @return 応答
     */
    public HttpResponse<String> detail(String token, Object roleId) {
        return send("GET", PATH + "/" + roleId, token, null);
    }

    /**
     * 名前を変える。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param name 名前
     * @return 応答
     */
    public HttpResponse<String> rename(String token, Object roleId, String name) {
        return renameJson(token, roleId, json(Map.of("name", name)));
    }

    /**
     * 本文の JSON で名前を変える。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param body 本文の JSON
     * @return 応答
     */
    public HttpResponse<String> renameJson(String token, Object roleId, String body) {
        return send("PUT", PATH + "/" + roleId, token, body);
    }

    /**
     * ロールを消す。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @return 応答
     */
    public HttpResponse<String> delete(String token, Object roleId) {
        return send("DELETE", PATH + "/" + roleId, token, null);
    }

    /**
     * 木の1段目を読む。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> schemas(String token, Object roleId, String... headers) {
        return send("GET", PATH + "/" + roleId + "/permissions/schemas", token, null, headers);
    }

    /**
     * スキーマの下のテーブルを読む。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param schema スキーマの名前（null なら引数を付けない）
     * @return 応答
     */
    public HttpResponse<String> tables(String token, Object roleId, String schema) {
        return send(
                "GET", PATH + "/" + roleId + "/permissions/tables" + query("schema", schema, null, null), token, null);
    }

    /**
     * テーブルの下のカラムを読む。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param schema スキーマの名前（null なら引数を付けない）
     * @param table テーブルの名前（null なら引数を付けない）
     * @return 応答
     */
    public HttpResponse<String> columns(String token, Object roleId, String schema, String table) {
        return send(
                "GET",
                PATH + "/" + roleId + "/permissions/columns" + query("schema", schema, "table", table),
                token,
                null);
    }

    /**
     * 本文の JSON で権限を保存する。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param body 本文の JSON
     * @return 応答
     */
    public HttpResponse<String> save(String token, Object roleId, String body) {
        return send("PUT", PATH + "/" + roleId + "/permissions", token, body);
    }

    /**
     * 本文の JSON で今の DSL に無い設定を消す。
     *
     * @param token アクセストークン
     * @param roleId ロールの ID
     * @param body 本文の JSON
     * @return 応答
     */
    public HttpResponse<String> clear(String token, Object roleId, String body) {
        return send("POST", PATH + "/" + roleId + "/permissions/clear", token, body);
    }

    /**
     * 任意の要求を送る（認可の表のテストで使う）。
     *
     * @param method 方法
     * @param path 道（問い合わせを含む）
     * @param token アクセストークン（無ければ null）
     * @param body 本文の JSON（無ければ null）
     * @param headers 足すヘッダー
     * @return 応答
     */
    public HttpResponse<String> send(String method, String path, String token, String body, String... headers) {
        HttpRequest.Builder builder = client.request(path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build());
    }

    private static String query(String name1, String value1, String name2, String value2) {
        StringBuilder query = new StringBuilder();
        append(query, name1, value1);
        append(query, name2, value2);
        return query.toString();
    }

    private static void append(StringBuilder query, String name, String value) {
        if (name == null || value == null) {
            return;
        }
        query.append(query.isEmpty() ? '?' : '&')
                .append(name)
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
