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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.config.ObservabilityConfig;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 外部エクスポートの結合テスト（FR10.4、BR4.3〜BR4.5、NFR10.7、NFR10.11）。
 *
 * <p>送り先は、テストの中で起動する受け手（受け取った要求を覚えるだけの HTTP サーバー）か、使っていない localhost の番号にする。
 * 外部のサービスには接続しない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.test-fixture.error-endpoints=true")
class ExternalExportIT {

    /** 受け手が受け取った要求（パスと本文）。 */
    record Received(String path, byte[] body) {}

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();

    static final HttpServer COLLECTOR = startCollector();

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

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
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

    @BeforeEach
    void clearReceived() {
        RECEIVED.clear();
    }

    /** 待ち行列に残っているトレースとログを送らせる。 */
    private void flush() {
        context.getBeanProvider(SdkTracerProvider.class)
                .ifAvailable(provider -> provider.forceFlush().join(10, TimeUnit.SECONDS));
        context.getBeanProvider(SdkLoggerProvider.class)
                .ifAvailable(provider -> provider.forceFlush().join(10, TimeUnit.SECONDS));
    }

    /** OTLP で指標を送る仕組み（実行時だけの依存のため、クラスの名前で見分ける）。 */
    private List<MeterRegistry> otlpMeterRegistries() {
        return context.getBeansOfType(MeterRegistry.class).values().stream()
                .flatMap(registry ->
                        registry instanceof io.micrometer.core.instrument.composite.CompositeMeterRegistry composite
                                ? composite.getRegistries().stream()
                                : java.util.stream.Stream.of(registry))
                .filter(registry ->
                        registry.getClass().getName().equals("io.micrometer.registry.otlp.OtlpMeterRegistry"))
                .toList();
    }

    private static boolean hasOtlpAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        return loggerContext
                        .getLogger(Logger.ROOT_LOGGER_NAME)
                        .getAppender(ObservabilityConfig.OtlpLogAppenderInstaller.APPENDER_NAME)
                != null;
    }

    @Test
    @DisplayName("by default no OTLP sender is created and nothing is sent even when an endpoint is configured")
    void disabledByDefault() {
        assertThat(context.getEnvironment().getProperty("mastersmith.observability.export.enabled"))
                .isEqualTo("false");
        assertThat(context.getBeansOfType(SpanExporter.class)).isEmpty();
        assertThat(otlpMeterRegistries()).isEmpty();
        assertThat(context.getBeansOfType(ObservabilityConfig.OtlpLogAppenderInstaller.class))
                .isEmpty();
        assertThat(hasOtlpAppender()).isFalse();

        new HttpTestClient(port).get("/api/no-such-api");
        flush();

        assertThat(RECEIVED).isEmpty();
    }

    @Nested
    @TestPropertySource(properties = "mastersmith.observability.export.enabled=true")
    @DisplayName("when export is enabled")
    class Enabled {

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("traces and logs are sent and exception messages are removed from the exported traces")
        void tracesAndLogsSent() {
            String secret = TestDatabase.randomSecret();

            assertThat(context.getBeansOfType(SpanExporter.class))
                    .isNotEmpty()
                    .allSatisfy((name, exporter) -> assertThat(exporter).isInstanceOf(SanitizingSpanExporter.class));
            assertThat(otlpMeterRegistries()).isNotEmpty();
            assertThat(hasOtlpAppender()).isTrue();

            HttpResponse<String> response =
                    new HttpTestClient(nestedPort).get("/api/test-fixture/boom?secret=" + secret);
            assertThat(response.statusCode()).isEqualTo(500);
            flush();

            assertThat(RECEIVED).extracting(Received::path).contains("/v1/traces", "/v1/logs");
            assertThat(RECEIVED)
                    .filteredOn(received -> received.path().equals("/v1/traces"))
                    .allSatisfy(received -> assertThat(new String(received.body(), StandardCharsets.ISO_8859_1))
                            .doesNotContain(secret));
        }
    }

    @Nested
    @TestPropertySource(properties = "mastersmith.observability.export.enabled=true")
    @DisplayName("when export is enabled but the receiver cannot be reached")
    class Unreachable {

        @DynamicPropertySource
        static void unreachable(DynamicPropertyRegistry registry) {
            registry.add("mastersmith.observability.export.endpoint", () -> "http://127.0.0.1:" + unusedPort());
        }

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("requests are answered normally")
        void requestsUnaffected() {
            HttpTestClient client = new HttpTestClient(nestedPort);

            long start = System.nanoTime();
            for (int i = 0; i < 5; i++) {
                assertThat(client.get("/api/problems/not-found").statusCode()).isEqualTo(200);
                assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
            }
            Map<String, Object> error = HttpTestClient.json(client.get("/api/no-such-api"));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(error).containsEntry("code", "NOT_FOUND");
            assertThat(elapsedMillis).isLessThan(10_000);
        }
    }
}
