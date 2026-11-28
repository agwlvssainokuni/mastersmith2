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
package cherry.mastersmith.internaldb.testsupport;

import static org.assertj.core.api.Assertions.fail;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 前提のデータを、HTTP の API と標準の SQL だけで作る部品（U3。H2 の機能別の部品 {@code RoleFixtures}・{@code UserAdminFixtures} などに
 * 依存しない）。利用者は標準の SQL で作り（bcrypt のハッシュは本体と同じ cost 4）、ロール・グループ・割り当て・DSL は初期管理者で
 * ログインして API で作る。一意の値は実行ごとの乱数で作り、メールアドレスは予約のドメイン（{@code example.com}）だけを使う。
 *
 * <p>失敗の文は状態コードと {@code code} だけを出し、メールアドレス・パスワード・トークンなどの値は出さない。
 */
public final class PortableFixture {

    private static final String REFRESH_COOKIE = "mastersmith_refresh";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 管理者のアクセストークンを作り直すまでの時間（アクセストークンの有効期限 5 分より短く）。 */
    private static final long TOKEN_REUSE_NANOS = 180_000_000_000L;

    private final PgApp app;

    private final HttpTestClient client;

    private String adminToken;

    private long adminTokenAt;

    /**
     * 作る。
     *
     * @param app 前提を作る先のアプリ
     */
    public PortableFixture(PgApp app) {
        this.app = app;
        this.client = app.client();
    }

    /**
     * 作った利用者（資格情報は出力に出さない）。
     *
     * @param userId 利用者の ID
     * @param email メールアドレス
     * @param password パスワード
     */
    public record Account(long userId, String email, String password) {
        @Override
        public String toString() {
            return "Account[userId=" + userId + ", email=***, password=***]";
        }
    }

    /**
     * 作った招待（トークンは出力に出さない）。
     *
     * @param invitationId 招待の ID
     * @param email 招待したメールアドレス
     * @param token リンクのトークン（43 文字）
     */
    public record Invitation(long invitationId, String email, String token) {
        @Override
        public String toString() {
            return "Invitation[invitationId=" + invitationId + ", email=***, token=***]";
        }
    }

    // ---- 利用者・ログイン ----

    /**
     * 利用者を標準の SQL で作る（表示の設定は初期値）。
     *
     * @param admin 管理者の印を付けるか
     * @return 作った利用者
     */
    public Account createUser(boolean admin) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        String email = "pg-user-" + HexFormat.of().formatHex(bytes) + "@example.com";
        String password = TestDatabase.randomSecret();
        String hash = new BCryptPasswordEncoder(4).encode(password);
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                                + " VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, hash);
            statement.setBoolean(3, admin);
            statement.setString(4, "PG User");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Account(keys.getLong(1), email, password);
            }
        } catch (SQLException e) {
            throw sqlFailure("the user could not be created", e);
        }
    }

    /**
     * ログインの応答を返す。
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return 応答
     */
    public HttpResponse<String> loginResponse(String email, String password) {
        return send("POST", "/api/auth/login", null, json(Map.of("email", email, "password", password)));
    }

    /**
     * ログインして、アクセストークンを返す（200 でなければ失敗にする）。
     *
     * @param account 利用者
     * @return アクセストークン
     */
    public String login(Account account) {
        return loginToken(account.email(), account.password());
    }

    /**
     * 初期管理者のアクセストークン（期限の前に作り直す）。
     *
     * @return アクセストークン
     */
    public synchronized String adminToken() {
        long now = System.nanoTime();
        if (adminToken == null || now - adminTokenAt > TOKEN_REUSE_NANOS) {
            adminToken = loginToken(app.adminEmail(), app.adminPassword());
            adminTokenAt = now;
        }
        return adminToken;
    }

    private String loginToken(String email, String password) {
        HttpResponse<String> response = loginResponse(email, password);
        if (response.statusCode() != 200) {
            fail("the login did not succeed: " + describe(response));
        }
        return (String) HttpTestClient.json(response).get("accessToken");
    }

    // ---- ロール・グループ・割り当て（初期管理者の API） ----

    /**
     * ロールを作る。
     *
     * @param name ロールの名前
     * @return ロールの ID
     */
    public long createRole(String name) {
        return idOf(expectStatus(adminCall("POST", "/api/admin/roles", json(Map.of("name", name))), 201));
    }

    /**
     * グループを作る。
     *
     * @param name グループの名前
     * @return グループの ID
     */
    public long createGroup(String name) {
        return idOf(expectStatus(adminCall("POST", "/api/admin/groups", json(Map.of("name", name))), 201));
    }

    /**
     * 乱数の入った、重ならない名前を作る（ロール・グループの名前用）。
     *
     * @param prefix 先頭の語
     * @return 名前
     */
    public static String uniqueName(String prefix) {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return prefix + "-" + HexFormat.of().formatHex(bytes);
    }

    /**
     * グループに利用者を加える。
     *
     * @param groupId グループの ID
     * @param userId 利用者の ID
     */
    public void addMember(long groupId, long userId) {
        expectSuccess(adminCall("POST", "/api/admin/groups/" + groupId + "/members", json(Map.of("userId", userId))));
    }

    /**
     * ロールを利用者に割り当てる。
     *
     * @param roleId ロールの ID
     * @param userId 利用者の ID
     */
    public void assignRoleToUser(long roleId, long userId) {
        expectSuccess(adminCall("POST", "/api/admin/roles/" + roleId + "/assignments", json(Map.of("userId", userId))));
    }

    /**
     * ロールをグループに割り当てる。
     *
     * @param roleId ロールの ID
     * @param groupId グループの ID
     */
    public void assignRoleToGroup(long roleId, long groupId) {
        expectSuccess(
                adminCall("POST", "/api/admin/roles/" + roleId + "/assignments", json(Map.of("groupId", groupId))));
    }

    // ---- 招待（標準の SQL。メールの送信に頼らない） ----

    /**
     * 待ち中の招待を標準の SQL で作る（メールは送らない。トークンのハッシュは SHA-256）。
     *
     * @param invitedBy 招待した利用者の ID
     * @param expiresAt 有効期限
     * @return 作った招待
     */
    public Invitation insertInvitation(long invitedBy, Instant expiresAt) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        String email = "pg-invitee-" + HexFormat.of().formatHex(bytes) + "@example.com";
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long id = insertInvitation(email, sha256(token.getBytes(StandardCharsets.UTF_8)), invitedBy, expiresAt);
        return new Invitation(id, email, token);
    }

    /**
     * 待ち中の招待を、トークンのハッシュを指定して標準の SQL で作る。
     *
     * @param email 招待するメールアドレス（そろえた小文字）
     * @param tokenHash トークンのハッシュ（バイト列）
     * @param invitedBy 招待した利用者の ID
     * @param expiresAt 有効期限
     * @return 招待の ID
     */
    public long insertInvitation(String email, byte[] tokenHash, long invitedBy, Instant expiresAt) {
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at,"
                                + " expires_at, send_result, state) VALUES (?, 'ja', ?, ?, CURRENT_TIMESTAMP, ?,"
                                + " 'SENT', 'PENDING')",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setBytes(2, tokenHash);
            statement.setLong(3, invitedBy);
            statement.setTimestamp(4, Timestamp.from(expiresAt));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw sqlFailure("the invitation could not be created", e);
        }
    }

    // ---- DSL（初期管理者の API） ----

    /**
     * 検証を通る最小の DSL（表が 1 つ・列が 2 つ）の本文を作る。
     *
     * @param table 表の名前
     * @return YAML の本文（UTF-8）
     */
    public static byte[] minimalDsl(String table) {
        String yaml = "version: 2\n"
                + "menus:\n"
                + "  - label: { ja: \"表\", en: \"Table\" }\n"
                + "    table: { schema: \"public\", name: \"" + table + "\" }\n"
                + "schemas:\n"
                + "  \"public\":\n"
                + "    label: { ja: \"public\", en: \"public\" }\n"
                + "    tables:\n"
                + "      " + table + ":\n"
                + "        label: { ja: \"" + table + "\", en: \"" + table + "\" }\n"
                + "        view: false\n"
                + "        primaryKey: []\n"
                + "        foreignKeys: []\n"
                + "        columns:\n"
                + column("code")
                + column("name");
        return yaml.getBytes(StandardCharsets.UTF_8);
    }

    private static String column(String name) {
        return "          " + name + ":\n"
                + "            label: { ja: \"" + name + "\", en: \"" + name + "\" }\n"
                + "            dbType: { name: VARCHAR, length: 10, precision: null, scale: null, nullable: true }\n"
                + "            formPart: text\n"
                + "            search: { enabled: false, collapsed: false }\n"
                + "            list: { visible: false, sortable: false }\n"
                + "            detail: { visible: true }\n"
                + "            validations: []\n";
    }

    /**
     * DSL をプレビューに置く（201 でなければ失敗にする）。
     *
     * @param yaml DSL の本文
     * @return プレビューの ID
     */
    public String placePreview(byte[] yaml) {
        HttpRequest request = client.request("/api/admin/dsl/preview?source=PASTE")
                .header("Authorization", "Bearer " + adminToken())
                .header("Content-Type", "application/yaml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(yaml))
                .build();
        HttpResponse<String> response = expectStatus(client.send(request), 201);
        return (String) HttpTestClient.json(response).get("previewId");
    }

    /**
     * プレビューを適用する（200 でなければ失敗にする）。
     *
     * @param previewId プレビューの ID
     */
    public void applyPreview(String previewId) {
        expectStatus(adminCall("POST", "/api/admin/dsl/apply", json(Map.of("previewId", previewId))), 200);
    }

    /**
     * 氏名とメールアドレスを指定して利用者を標準の SQL で作る（検索の見本用。パスワードは乱数で、ログインには使わない）。
     *
     * @param email メールアドレス（そろえた小文字）
     * @param displayName 氏名
     * @return 利用者の ID
     */
    public long createUserNamed(String email, String displayName) {
        String hash = new BCryptPasswordEncoder(4).encode(TestDatabase.randomSecret());
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                                + " VALUES (?, ?, FALSE, CURRENT_TIMESTAMP, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, hash);
            statement.setString(3, displayName);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw sqlFailure("the user could not be created", e);
        }
    }

    /**
     * リフレッシュトークンの行を、ハッシュを指定して標準の SQL で作る（1 時間有効）。
     *
     * @param userId 利用者の ID
     * @param tokenHash トークンのハッシュ（32 バイト）
     */
    public void insertRefreshToken(long userId, byte[] tokenHash) {
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO refresh_tokens (user_id, token_hash, issued_at, expires_at)"
                                + " VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1' HOUR)")) {
            statement.setLong(1, userId);
            statement.setBytes(2, tokenHash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw sqlFailure("the refresh token could not be created", e);
        }
    }

    /**
     * 値の SHA-256（UTF-8 のバイト列）。
     *
     * @param value 値
     * @return 32 バイトのハッシュ
     */
    public static byte[] sha256Of(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    // ---- 認証・監査・権限の補助 ----

    /**
     * 自分の配信元（更新・ログアウトの Origin に入れる値）。
     *
     * @return 配信元
     */
    public String origin() {
        return "http://localhost:" + app.port();
    }

    /**
     * リフレッシュトークンの Cookie で更新する。
     *
     * @param cookie Cookie の値（無ければ null）
     * @param origin Origin（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> refresh(String cookie, String origin) {
        return cookiePost("/api/auth/session/refresh", cookie, origin);
    }

    /**
     * リフレッシュトークンの Cookie でログアウトする。
     *
     * @param cookie Cookie の値（無ければ null）
     * @param origin Origin（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> logout(String cookie, String origin) {
        return cookiePost("/api/auth/session/logout", cookie, origin);
    }

    private HttpResponse<String> cookiePost(String path, String cookie, String origin) {
        HttpRequest.Builder builder = client.request(path).POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", REFRESH_COOKIE + "=" + cookie);
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return client.send(builder.build());
    }

    /**
     * 応答の {@code Set-Cookie} のうち、リフレッシュトークンの Cookie の行（無ければ空の文字列）。
     *
     * @param response 応答
     * @return {@code Set-Cookie} の行
     */
    public static String setCookieLine(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(line -> line.startsWith(REFRESH_COOKIE + "="))
                .findFirst()
                .orElse("");
    }

    /**
     * 応答の {@code Set-Cookie} から、リフレッシュトークンの値を取り出す。
     *
     * @param response 応答
     * @return 値
     */
    public static String cookieValue(HttpResponse<String> response) {
        String line = setCookieLine(response);
        return line.substring(REFRESH_COOKIE.length() + 1, line.indexOf(';'));
    }

    /**
     * ログイン・更新の応答のアクセストークン。
     *
     * @param response 応答
     * @return アクセストークン
     */
    public static String accessToken(HttpResponse<String> response) {
        return (String) HttpTestClient.json(response).get("accessToken");
    }

    /**
     * 監査の行の数（種類と結果で数える）。
     *
     * @param eventType 種類（{@code AuditEventType} の名前）
     * @param result 結果（{@code SUCCESS}・{@code FAILURE} など）
     * @return 件数
     */
    public int auditCount(String eventType, String result) {
        return count("SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND result = ?", eventType, result);
    }

    /**
     * 監査の行の数（種類で数える）。
     *
     * @param eventType 種類
     * @return 件数
     */
    public int auditCount(String eventType) {
        return count("SELECT COUNT(*) FROM audit_events WHERE event_type = ?", eventType);
    }

    private int count(String sql, String... arguments) {
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setString(i + 1, arguments[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw sqlFailure("the count could not be read", e);
        }
    }

    /**
     * 初期管理者の利用者 ID。
     *
     * @return 利用者 ID
     */
    public long adminUserId() {
        try (Connection connection = app.database().connect();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT user_id FROM users WHERE email = ?")) {
            statement.setString(1, app.adminEmail());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw sqlFailure("the admin user could not be read", e);
        }
    }

    /**
     * ロールに、表の主権限を標準の SQL で与える（メニューの出し分けの前提）。
     *
     * @param roleId ロールの ID
     * @param schema スキーマ名
     * @param table 表の名前
     * @param main 主権限（{@code NONE}・{@code READ}・{@code FULL}）
     */
    public void grantTable(long roleId, String schema, String table, String main) {
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO permission_settings (role_id, schema_name, table_name, column_name,"
                                + " main_permission, updated_at) VALUES (?, ?, ?, '', ?, CURRENT_TIMESTAMP)")) {
            statement.setLong(1, roleId);
            statement.setString(2, schema);
            statement.setString(3, table);
            statement.setString(4, main);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw sqlFailure("the permission could not be created", e);
        }
    }

    /**
     * 問題の応答（状態コードと {@code code}）を確かめる。失敗の文は状態コードと {@code code} だけを出す。
     *
     * @param response 応答
     * @param status 期待する状態コード
     * @param code 期待する {@code code}
     */
    public static void assertProblem(HttpResponse<String> response, int status, String code) {
        String actual = describe(response);
        if (response.statusCode() != status || !actual.endsWith(" code=" + code)) {
            fail("unexpected problem: expected status=" + status + " code=" + code + ", actual " + actual);
        }
    }

    // ---- 標準の SQL の補助 ----

    /**
     * 自動確定の接続で文を 1 つ流す（前提の作成・後始末用。値を含む文は出力に出さない）。
     *
     * @param sql 文
     */
    public void executeSql(String sql) {
        try (Connection connection = app.database().connect();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw sqlFailure("the statement failed", e);
        }
    }

    /**
     * 1 行 1 列の数を返す問い合わせを流す。
     *
     * @param sql 問い合わせ
     * @return 値
     */
    public long queryLong(String sql) {
        try (Connection connection = app.database().connect();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw sqlFailure("the query failed", e);
        }
    }

    /**
     * 1 行 1 列の文字列を返す問い合わせを流す（行が無ければ null）。
     *
     * @param sql 問い合わせ
     * @return 値
     */
    public String queryString(String sql) {
        try (Connection connection = app.database().connect();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw sqlFailure("the query failed", e);
        }
    }

    // ---- 呼び出しの共通 ----

    /**
     * JSON の本文の要求を送る。
     *
     * @param method HTTP のメソッド
     * @param path パス（問い合わせを含んでよい）
     * @param token アクセストークン（無ければ null）
     * @param jsonBody JSON の本文（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> send(String method, String path, String token, String jsonBody) {
        HttpRequest.Builder builder = client.request(path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
            body = HttpRequest.BodyPublishers.ofString(jsonBody);
        }
        return client.send(builder.method(method, body).build());
    }

    /**
     * 初期管理者のトークンで要求を送る。
     *
     * @param method HTTP のメソッド
     * @param path パス
     * @param jsonBody JSON の本文（無ければ null）
     * @return 応答
     */
    public HttpResponse<String> adminCall(String method, String path, String jsonBody) {
        return send(method, path, adminToken(), jsonBody);
    }

    /**
     * 値を JSON の文字列にする。
     *
     * @param value 値
     * @return JSON
     */
    public static String json(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /**
     * 応答の本文から、作ったものの ID（{@code roleId}・{@code groupId} など、名前が {@code Id} で終わる最初の項目）を取り出す。
     *
     * @param response 応答
     * @return ID
     */
    public static long idOf(HttpResponse<String> response) {
        for (Map.Entry<String, Object> entry : HttpTestClient.json(response).entrySet()) {
            if ((entry.getKey().endsWith("Id") || entry.getKey().equals("id"))
                    && entry.getValue() instanceof Number n) {
                return n.longValue();
            }
        }
        throw new IllegalStateException("the response has no id field");
    }

    /**
     * 応答の状態コードと {@code code} 項目だけを文にする（本文のほかの値は出さない）。
     *
     * @param response 応答
     * @return 文
     */
    public static String describe(HttpResponse<String> response) {
        String code = "";
        try {
            Object value = HttpTestClient.json(response).get("code");
            code = value == null ? "" : " code=" + value;
        } catch (RuntimeException e) {
            // 本文が JSON でなければ状態コードだけにする。
        }
        return "status=" + response.statusCode() + code;
    }

    private static HttpResponse<String> expectStatus(HttpResponse<String> response, int expected) {
        if (response.statusCode() != expected) {
            fail("unexpected response: expected=" + expected + " " + describe(response));
        }
        return response;
    }

    private static void expectSuccess(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            fail("unexpected response: " + describe(response));
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    private static IllegalStateException sqlFailure(String message, SQLException e) {
        return new IllegalStateException(message + ": SQLState=" + e.getSQLState());
    }
}
