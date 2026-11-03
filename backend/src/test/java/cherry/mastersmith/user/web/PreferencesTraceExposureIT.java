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
package cherry.mastersmith.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 外へ送る設定を有効にしたとき、プリファレンスの保存の要求で、拒否された表示のタイムゾーンの値と氏名がトレースの属性・ログの
 * エクスポートに含まれないことの結合テスト（Intent 261102-timezone-ui-polish の U2、SD3）。送り先はテストの中で起動する受け手。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.observability.export.enabled=true")
class PreferencesTraceExposureIT {

    static final List<byte[]> RECEIVED = new CopyOnWriteArrayList<>();

    static final HttpServer COLLECTOR = startCollector();

    @TempDir
    static Path tempDir;

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                exchange.getRequestBody().transferTo(body);
                RECEIVED.add(body.toByteArray());
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

    @Test
    @DisplayName("a rejected zone value and the name are not in the exported spans or logs")
    void notExported() {
        String email = "zone-trace-" + UUID.randomUUID() + "@example.com";
        String password = TestDatabase.randomSecret();
        TestUserAccounts.create(userAccountService, email, password, false);
        String token = AuthApi.accessToken(new AuthApi(port).login(email, password));
        String markZone = "ZONEMARK" + UUID.randomUUID().toString().replace("-", "");
        String markName = "NAMEMARK" + UUID.randomUUID().toString().replace("-", "");
        RECEIVED.clear();

        new MeApi(port).putPreferencesWithZone(token, markName, "en", "dark", "lg", markZone);
        new MeApi(port).putPreferencesWithZone(token, markName, "en", "dark", "lg", "Asia/Tokyo");
        context.getBeanProvider(SdkTracerProvider.class)
                .ifAvailable(provider -> provider.forceFlush().join(10, TimeUnit.SECONDS));
        context.getBeanProvider(SdkLoggerProvider.class)
                .ifAvailable(provider -> provider.forceFlush().join(10, TimeUnit.SECONDS));

        assertThat(RECEIVED).as("外へ送られた").isNotEmpty();
        for (byte[] body : RECEIVED) {
            String text = new String(body, StandardCharsets.ISO_8859_1);
            assertThat(text).doesNotContain(markZone).doesNotContain(markName);
        }
    }
}
