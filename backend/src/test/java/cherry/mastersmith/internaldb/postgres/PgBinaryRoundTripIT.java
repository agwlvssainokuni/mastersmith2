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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 大きなバイナリとハッシュ（BR6.1・BR6.2）。(1) DSL の本文は上限 10,485,760 バイトちょうどまで、全バイト値を含む本文が長さと SHA-256 を変えずに往復し、
 * API でも上限ちょうどは受け付けて +1 バイトは 413 {@code DSL_TOO_LARGE}（{@code DslAdminApiIT} の応答）。(2) 全バイト値のうち先頭が 0x00・末尾が 0xFF
 * （と、その逆）の 32 バイトのハッシュを、リフレッシュトークンと招待の両方で保存して照合できる。ハッシュ・本文の値は出力に出さない（長さ・一致の真偽・SHA-256 の
 * 一致だけを使う）。テストの JVM のヒープは 1g のまま。
 */
@InternalDbPostgresIT
class PgBinaryRoundTripIT {

    private static final int TEN_MB = 10 * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static PgApp app;

    private static PortableFixture fixture;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    /** 検証を通る DSL を、コメントの行で指定のバイト数ちょうどにする（{@code DslAdminApiIT} と同じ作り方）。 */
    private static byte[] padded(byte[] dsl, int size) {
        StringBuilder text = new StringBuilder(new String(dsl, StandardCharsets.UTF_8));
        int remaining = size - dsl.length;
        String line = "#" + "x".repeat(998) + "\n";
        while (remaining >= line.length() + 2) {
            text.append(line);
            remaining -= line.length();
        }
        text.append("#").append("y".repeat(remaining - 2)).append("\n");
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        assertThat(bytes).hasSize(size);
        return bytes;
    }

    /** 乱数の 43 文字のトークンのうち、ハッシュが条件に当たるものを探す（約 65,000 回の試行）。 */
    private static String tokenWhere(Predicate<byte[]> hashCondition) {
        while (true) {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            if (hashCondition.test(PortableFixture.sha256Of(token))) {
                return token;
            }
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    @Test
    @DisplayName("a 10,485,760 byte body with every byte value keeps its length and SHA-256 through the preview table")
    void bodyWithEveryByteValueRoundTrips() {
        DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
        TransactionTemplate tx = new TransactionTemplate(app.context().getBean(PlatformTransactionManager.class));
        byte[] body = new byte[TEN_MB];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i & 0xFF);
        }
        byte[] expected = sha256(body);
        UUID previewId = UUID.randomUUID();
        fixture.executeSql("DELETE FROM dsl_previews");

        tx.execute(status ->
                previews.place(previewId, body, hex(expected), DslSource.PASTE, fixture.adminUserId(), Instant.now()));

        assertThat(fixture.queryLong("SELECT length(yaml_bytes) FROM dsl_previews WHERE preview_slot = 1"))
                .isEqualTo(TEN_MB);
        assertThat(fixture.queryString("SELECT encode(sha256(yaml_bytes), 'hex') FROM dsl_previews"))
                .as("the SHA-256 computed by the database")
                .isEqualTo(hex(expected));
        DslContent<DslPreviewRef> read =
                tx.execute(status -> previews.findContent()).orElseThrow();
        assertThat(read.yamlBytes()).as("the length read back").hasSize(TEN_MB);
        assertThat(MessageDigest.isEqual(sha256(read.yamlBytes()), expected))
                .as("the SHA-256 of the bytes read back")
                .isTrue();
    }

    @Test
    @DisplayName(
            "through the API the exact limit is accepted and stored in full, and one byte over is 413 DSL_TOO_LARGE")
    void apiLimitBoundary() throws IOException {
        byte[] exact = padded(PortableFixture.minimalDsl("big_t"), TEN_MB);
        HttpRequest request = app.client()
                .request("/api/admin/dsl/preview?source=UPLOAD")
                .header("Authorization", "Bearer " + fixture.adminToken())
                .header("Content-Type", "application/yaml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(exact))
                .build();

        HttpResponse<String> accepted = app.client().send(request);
        String rejected = headersOnly(TEN_MB + 1L);

        assertThat(accepted.statusCode()).isEqualTo(201);
        assertThat(fixture.queryLong("SELECT length(yaml_bytes) FROM dsl_previews WHERE preview_slot = 1"))
                .isEqualTo(TEN_MB);
        assertThat(fixture.queryString("SELECT encode(sha256(yaml_bytes), 'hex') FROM dsl_previews"))
                .isEqualTo(hex(sha256(exact)));
        assertThat(rejected).startsWith("HTTP/1.1 413").contains("\"code\":\"DSL_TOO_LARGE\"");
        assertThat(HttpTestClient.json(accepted)).containsKeys("previewId", "dslHash");
    }

    /** 本文を送らずに Content-Length だけを宣言した要求を送り、応答の全文を返す（{@code DslApi.submitHeadersOnly} と同じ作り方）。 */
    private static String headersOnly(long contentLength) throws IOException {
        try (Socket socket = new Socket("localhost", app.port())) {
            socket.setSoTimeout(15_000);
            String head = "POST /api/admin/dsl/preview?source=PASTE HTTP/1.1\r\n"
                    + "Host: localhost:" + app.port() + "\r\n"
                    + "Content-Type: application/yaml\r\n"
                    + "Content-Length: " + contentLength + "\r\n"
                    + "Connection: close\r\n"
                    + "Authorization: Bearer " + fixture.adminToken() + "\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            try {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    all.write(buffer, 0, read);
                    if (all.toString(StandardCharsets.UTF_8).contains("DSL_TOO_LARGE")) {
                        break;
                    }
                }
            } catch (IOException e) {
                // サーバーが本文を読まずに閉じると、応答の後で接続が切られることがある。そこまでに読めた分で判定する。
            }
            return all.toString(StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName(
            "32-byte hashes starting with 0x00 and ending with 0xFF (and the reverse) match for refresh tokens and invitations")
    void thirtyTwoByteHashesMatch() {
        String leadingZero = tokenWhere(hash -> hash[0] == 0 && hash[31] == (byte) 0xFF);
        String trailingZero = tokenWhere(hash -> hash[0] == (byte) 0xFF && hash[31] == 0);
        PortableFixture.Account member = fixture.createUser(false);
        long adminId = fixture.adminUserId();

        for (String token : new String[] {leadingZero, trailingZero}) {
            byte[] hash = PortableFixture.sha256Of(token);
            fixture.insertRefreshToken(member.userId(), hash);
            HttpResponse<String> refreshed = fixture.refresh(token, fixture.origin());
            assertThat(refreshed.statusCode())
                    .as("refresh with the stored hash")
                    .isEqualTo(200);
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM refresh_tokens WHERE encode(token_hash, 'hex') = '"
                            + hex(hash) + "' AND length(token_hash) = 32"))
                    .as("the stored refresh hash is 32 bytes and unchanged")
                    .isOne();

            String email = "pg-hash-" + hex(hash).substring(0, 12) + "@example.com";
            long invitationId =
                    fixture.insertInvitation(email, hash, adminId, Instant.now().plus(1, ChronoUnit.HOURS));
            HttpResponse<String> verified = fixture.send(
                    "POST", "/api/registration/verify", null, PortableFixture.json(Map.of("token", token)));
            assertThat(verified.statusCode()).as("verify with the stored hash").isEqualTo(200);
            assertThat(HttpTestClient.json(verified)).containsEntry("email", email);
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM invitations WHERE invitation_id = " + invitationId
                            + " AND length(token_hash) = 32 AND encode(token_hash, 'hex') = '" + hex(hash) + "'"))
                    .isOne();
        }
    }
}
