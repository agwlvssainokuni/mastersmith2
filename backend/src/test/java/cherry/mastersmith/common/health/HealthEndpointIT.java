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
package cherry.mastersmith.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * ヘルスチェックの起動確認テスト（FR1.1、BR1.1〜BR1.4、NFR1.2、NFR10.8、NFR10.10）。
 *
 * <p>アプリを起動し、ログインせずに {@code /actuator/health} を呼ぶ。内部DBが応答しない場合は、ヘルスの確認が使う接続だけを
 * 問い合わせが止まる偽のものに差し替えたコンテキストで確かめる（起動時の Flyway は本物の H2 を使う）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Test
    @DisplayName("health returns 200 with only the UP status and no details without login")
    void upWithoutDetails() {
        HttpResponse<String> response = new HttpTestClient(port).get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(response)).containsOnlyKeys("status").containsEntry("status", "UP");
    }

    @Test
    @DisplayName("health response is not stored by caches")
    void noStore() {
        assertThat(new HttpTestClient(port).get("/actuator/health").headers().firstValue("Cache-Control"))
                .contains("no-store");
    }

    @Test
    @DisplayName("asking for details does not reveal components")
    void detailsNotRevealed() {
        HttpTestClient client = new HttpTestClient(port);

        assertThat(client.get("/actuator/health?showDetails=true").body()).doesNotContain("components");
        assertThat(client.get("/actuator/health/timeBoundedDb").body()).doesNotContain("details");
    }

    @Nested
    @DisplayName("when the internal database stops responding after startup")
    class DatabaseStopsResponding {

        /** 偽の接続を止めておく（後始末で外す）。 */
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        static final Duration TIMEOUT = Duration.ofMillis(300);

        @TestBean(name = "timeBoundedDbHealthIndicator")
        TimeBoundedDbHealthIndicator indicator;

        static TimeBoundedDbHealthIndicator indicator() throws SQLException {
            DataSource stalling = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            when(stalling.getConnection()).thenAnswer(invocation -> {
                RELEASE.await(30, TimeUnit.SECONDS);
                return connection;
            });
            return new TimeBoundedDbHealthIndicator(stalling, new HealthProperties(TIMEOUT));
        }

        @AfterAll
        static void release() {
            RELEASE.countDown();
        }

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("health returns 503 DOWN within the time limit while the screen and problem pages still respond")
        void downWithinTimeLimit() {
            HttpTestClient client = new HttpTestClient(nestedPort);

            long start = System.nanoTime();
            HttpResponse<String> response = client.get("/actuator/health");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(HttpTestClient.json(response)).containsOnlyKeys("status").containsEntry("status", "DOWN");
            assertThat(elapsedMillis).isLessThan(TIMEOUT.toMillis() + 5_000);

            assertThat(client.get("/").statusCode()).isEqualTo(200);
            assertThat(client.get("/api/problems/internal-error").statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("repeated health requests keep answering DOWN without waiting for the stalled check")
        void repeatedRequestsAnswerDown() {
            HttpTestClient client = new HttpTestClient(nestedPort);

            for (int i = 0; i < 3; i++) {
                assertThat(client.get("/actuator/health").statusCode()).isEqualTo(503);
            }
        }
    }
}
