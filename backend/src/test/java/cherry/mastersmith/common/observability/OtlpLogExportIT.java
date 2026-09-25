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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 外部へ送るログの結合テスト（260924-followup-fixes の FR1.1、FR1.2、FR1.4）。
 *
 * <p>送り出しを有効にし、送り先はテストの中で起動する受け手（受け取った要求を覚えるだけの HTTP サーバー）にする。外部のサービスには
 * 接続しない。届いた本文は OTLP の protobuf のため、新しい依存を足さずに、キーと文字列の値の組のバイト列
 * （{@code キー 0x12 長さ 0x0A 長さ 値}）と、値の UTF-8 のバイト列を探して確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mastersmith.auth.password.bcrypt-cost=4", "mastersmith.observability.export.enabled=true"})
@Import(FailingAuditEventRepositoryConfig.class)
class OtlpLogExportIT {

    /** 伏せるキー。 */
    private static final List<String> MASKED_KEYS = List.of("email", "enteredEmail", "sourceIp", "userAgent");

    /** 受け手が受け取った要求（パスと本文）。 */
    record Received(String path, byte[] body) {}

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();

    static final HttpServer COLLECTOR = startCollector();

    /** 初期管理者のメールアドレス（例示用のドメイン）。起動のときに作成の INFO が出る。 */
    static final String ADMIN_EMAIL = "otlp-admin-" + UUID.randomUUID() + "@example.com";

    static final String ADMIN_PASSWORD = TestDatabase.randomSecret();

    @TempDir
    static Path tempDir;

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                exchange.getRequestBody().transferTo(body);
                RECEIVED.add(new Received(exchange.getRequestURI().getPath(), body.toByteArray()));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
        registry.add(
                "mastersmith.observability.export.endpoint",
                () -> "http://127.0.0.1:" + COLLECTOR.getAddress().getPort());
        registry.add("mastersmith.auth.initial-admin.email", () -> ADMIN_EMAIL);
        registry.add("mastersmith.auth.initial-admin.password", () -> ADMIN_PASSWORD);
    }

    @AfterAll
    static void stopCollector() {
        COLLECTOR.stop(0);
    }

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    FailingAuditEventRepository auditRepository;

    @BeforeEach
    void setUp() {
        auditRepository.mode(Mode.NONE);
    }

    /** 待ち行列に残っているトレースとログを送らせる。 */
    private void flush() {
        context.getBeanProvider(SdkTracerProvider.class)
                .ifAvailable(provider -> provider.forceFlush().join(10, TimeUnit.SECONDS));
        context.getBeanProvider(SdkLoggerProvider.class)
                .ifAvailable(provider -> provider.forceFlush().join(10, TimeUnit.SECONDS));
    }

    /** 受け手が受け取った、指定したパスの本文をつなげたもの（起動の時からのすべて）。 */
    private static byte[] received(String path) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        RECEIVED.stream().filter(r -> r.path().equals(path)).forEach(r -> all.writeBytes(r.body()));
        return all.toByteArray();
    }

    /** OTLP の属性（KeyValue）のうち、キーと文字列の値の組のバイト列（値の長さは 126 バイトまで）。 */
    private static byte[] stringAttribute(String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(keyBytes);
        out.write(0x12);
        out.write(valueBytes.length + 2);
        out.write(0x0A);
        out.write(valueBytes.length);
        out.writeBytes(valueBytes);
        return out.toByteArray();
    }

    /** キーの後に値（フィールド番号 2）が続くバイト列。値の型を問わずに、そのキーの属性を数えるために使う。 */
    private static byte[] anyAttribute(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[keyBytes.length + 1];
        System.arraycopy(keyBytes, 0, out, 0, keyBytes.length);
        out[keyBytes.length] = 0x12;
        return out;
    }

    private static int count(byte[] haystack, byte[] needle) {
        int count = 0;
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            count++;
        }
        return count;
    }

    private static boolean contains(byte[] haystack, String value) {
        return count(haystack, value.getBytes(StandardCharsets.UTF_8)) > 0;
    }

    /** User-Agent を付けてログインする。 */
    private HttpResponse<String> login(String email, String password, String userAgent) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        HttpTestClient client = new HttpTestClient(port);
        return client.send(client.request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String newUser(String password) {
        String email = "otlp-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(password), false);
        return email;
    }

    @Test
    @DisplayName("key-value pairs are exported as log attributes and the log exporter is wrapped")
    void keyValuesExported() {
        assertThat(context.getBeansOfType(LogRecordExporter.class))
                .isNotEmpty()
                .allSatisfy((name, exporter) -> assertThat(exporter).isInstanceOf(SanitizingLogRecordExporter.class));
        String password = TestDatabase.randomSecret();
        String email = newUser(password);
        auditRepository.mode(Mode.APPEND_FAILURE);

        assertThat(login(email, password, "otlp-test-agent").statusCode()).isEqualTo(200);
        flush();

        byte[] logs = received("/v1/logs");
        assertThat(count(logs, stringAttribute("auditEventType", "LOGIN_SUCCEEDED")))
                .isPositive();
        assertThat(count(logs, anyAttribute("exceptionType"))).isPositive();
    }

    @Test
    @DisplayName("personal values in exported logs are masked")
    void personalValuesMasked() {
        String password = TestDatabase.randomSecret();
        String email = newUser(password);
        String userAgent = "otlp-test-agent-" + UUID.randomUUID();
        auditRepository.mode(Mode.APPEND_FAILURE);

        assertThat(login(email, password, userAgent).statusCode()).isEqualTo(200);
        flush();

        byte[] logs = received("/v1/logs");
        // 初期管理者の作成の INFO（起動のとき）と、監査の書き込みの失敗の ERROR の両方で、キーは残り値は伏せられる。
        for (String key : MASKED_KEYS) {
            assertThat(count(logs, stringAttribute(key, SanitizingLogRecordExporter.MASK)))
                    .as(key)
                    .isPositive()
                    .isEqualTo(count(logs, anyAttribute(key)));
        }
        assertThat(contains(logs, ADMIN_EMAIL)).isFalse();
        assertThat(contains(logs, email)).isFalse();
        assertThat(contains(logs, userAgent)).isFalse();
    }

    @Test
    @DisplayName("passwords, tokens and the signing key are never exported")
    void secretsNeverExported() {
        String password = TestDatabase.randomSecret();
        String email = newUser(password);
        AuthApi api = new AuthApi(port);

        HttpResponse<String> login = login(email, password, "otlp-test-agent");
        assertThat(login.statusCode()).isEqualTo(200);
        String accessToken = AuthApi.accessToken(login);
        String refreshToken = AuthApi.cookieValue(login);
        HttpResponse<String> refreshed = api.refresh(refreshToken, api.origin());
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String newRefreshToken = AuthApi.cookieValue(refreshed);
        String newAccessToken = AuthApi.accessToken(refreshed);
        assertThat(api.logout(newRefreshToken, api.origin()).statusCode()).isLessThan(300);
        assertThat(login(email, "まちがい-" + password, "otlp-test-agent").statusCode())
                .isEqualTo(401);
        flush();

        String signingKey =
                context.getEnvironment().getRequiredProperty(TestSigningKeyEnvironmentPostProcessor.SIGNING_KEY);
        List<String> secrets = List.of(
                password, ADMIN_PASSWORD, accessToken, refreshToken, newRefreshToken, newAccessToken, signingKey);
        assertThat(received("/v1/logs")).isNotEmpty();
        assertThat(received("/v1/traces")).isNotEmpty();
        for (String path : List.of("/v1/logs", "/v1/traces")) {
            byte[] body = received(path);
            for (String secret : secrets) {
                assertThat(contains(body, secret)).as(path).isFalse();
            }
        }
    }
}
