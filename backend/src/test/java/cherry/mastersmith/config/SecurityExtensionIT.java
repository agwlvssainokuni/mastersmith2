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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.security.ApiDefaultAccess;
import cherry.mastersmith.common.security.SecurityRuleContributor;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.common.testsupport.TestSecurityExtensions;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** U2・U3 が決まりを足す差し込み口の結合テスト（security-design 3章）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.test-fixture.error-endpoints=true")
class SecurityExtensionIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Test
    @DisplayName("with U1 alone every /api/** request is permitted")
    void u1AlonePermitsApi() {
        HttpTestClient client = new HttpTestClient(port);

        assertThat(client.get("/api/test-fixture/number?value=1").statusCode()).isEqualTo(200);
        assertThat(client.get("/api/test-fixture/business").statusCode()).isEqualTo(409);
        assertThat(client.get("/api/no-such-api").statusCode()).isEqualTo(404);
    }

    @Nested
    @TestPropertySource(properties = "mastersmith.test-fixture.security-contributors=true")
    @DisplayName("with contributors of order 100 and 200")
    class WithContributors {

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("contributors are applied in ascending order so the earlier rule wins")
        void appliedInOrder() {
            HttpTestClient client = new HttpTestClient(nestedPort);

            assertThat(TestSecurityExtensions.CONTRIBUTION_ORDER).containsSubsequence(100, 200);
            assertThat(client.get("/api/test-fixture/number?value=1").statusCode())
                    .isEqualTo(200);
            assertThat(client.get("/api/test-fixture/business").statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("U1 public rules still come first")
        void publicRulesFirst() {
            HttpTestClient client = new HttpTestClient(nestedPort);

            assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
            assertThat(client.get("/api/problems/not-found").statusCode()).isEqualTo(200);
        }
    }

    @Nested
    @TestPropertySource(properties = "mastersmith.test-fixture.api-default-access=true")
    @DisplayName("with an ApiDefaultAccess that requires authentication")
    class WithDefaultAccess {

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("/api/** requires login while health, problem pages and the screen stay public")
        void apiRequiresLogin() {
            HttpTestClient client = new HttpTestClient(nestedPort);

            assertThat(client.get("/api/test-fixture/number?value=1").statusCode())
                    .isEqualTo(401);
            assertThat(client.get("/api/no-such-api").statusCode()).isEqualTo(401);
            assertThat(client.get("/actuator/health").statusCode()).isEqualTo(200);
            assertThat(client.get("/api/problems/not-found").statusCode()).isEqualTo(200);
            assertThat(client.get("/").statusCode()).isEqualTo(200);
        }
    }

    /** 同じ order の決まりが2つある場合の役（部品の走査の対象にしないよう、注釈を付けずに起動の元として渡す）。 */
    static class DuplicateOrderContributors {

        @Bean
        SecurityRuleContributor firstContributor() {
            return contributor(150);
        }

        @Bean
        SecurityRuleContributor secondContributor() {
            return contributor(150);
        }

        private static SecurityRuleContributor contributor(int order) {
            return new SecurityRuleContributor() {
                @Override
                public void contribute(HttpSecurity http) {
                    // 起動の検査で失敗するため、決まりは足さない。
                }

                @Override
                public int getOrder() {
                    return order;
                }
            };
        }
    }

    /** API の既定の扱いが2つある場合の役。 */
    static class TwoDefaultAccesses {

        @Bean
        ApiDefaultAccess firstDefaultAccess() {
            return () -> true;
        }

        @Bean
        ApiDefaultAccess secondDefaultAccess() {
            return () -> false;
        }
    }

    private void start(Class<?> extra) {
        new SpringApplicationBuilder(MastersmithApplication.class, extra)
                .run("--spring.datasource.url=" + TestDatabase.url(tempDir.resolve("startup")), "--server.port=0")
                .close();
    }

    @Test
    @DisplayName("duplicate contributor order stops the startup")
    void duplicateOrderFails() {
        assertThatThrownBy(() -> start(DuplicateOrderContributors.class))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[150]");
    }

    @Test
    @DisplayName("two ApiDefaultAccess beans stop the startup")
    void twoDefaultAccessesFail() {
        assertThatThrownBy(() -> start(TwoDefaultAccesses.class))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ApiDefaultAccess");
    }
}
