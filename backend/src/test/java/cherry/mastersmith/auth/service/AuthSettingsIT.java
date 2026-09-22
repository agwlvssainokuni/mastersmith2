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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 設定を変えた起動が効き、範囲外の値で起動が止まることの結合テスト（NFR6.3、BR3.7）。 */
class AuthSettingsIT {

    @TempDir
    Path tempDir;

    private ConfigurableApplicationContext start(String name, String... args) {
        String[] all = new String[args.length + 3];
        all[0] = "--spring.datasource.url=" + TestDatabase.url(tempDir.resolve(name));
        all[1] = "--server.port=0";
        all[2] = "--mastersmith.auth.password.bcrypt-cost=4";
        System.arraycopy(args, 0, all, 3, args.length);
        return new SpringApplicationBuilder(MastersmithApplication.class).run(all);
    }

    @Test
    @DisplayName("changed settings take effect: a threshold of three locks on the third failure")
    void changedSettings() {
        try (ConfigurableApplicationContext context = start(
                "changed",
                "--mastersmith.auth.lock.threshold=3",
                "--mastersmith.auth.lock.duration=10m",
                "--mastersmith.auth.access-token-ttl=2m")) {
            AuthProperties properties = context.getBean(AuthProperties.class);
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(2));
            context.getBean(UserAccountService.class)
                    .createUser("settings@example.com", new Password("正しいパスワード-1234"), false);
            LoginService login = context.getBean(LoginService.class);
            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> login.login(
                                new LoginCommand("settings@example.com", new Password("まちがい")),
                                new ClientInfo("127.0.0.1", "IT", null)))
                        .isInstanceOf(BusinessException.class);
            }
            Map<String, Object> state = new JdbcTemplate(context.getBean(DataSource.class))
                    .queryForMap("SELECT s.consecutive_failures, s.locked_until FROM login_attempt_states s"
                            + " JOIN users u ON u.user_id = s.subject_id WHERE u.email = 'settings@example.com'");
            assertThat(state.get("CONSECUTIVE_FAILURES")).isEqualTo(3);
            assertThat(state.get("LOCKED_UNTIL")).isNotNull();
        }
    }

    @Test
    @DisplayName("the default settings are applied when nothing is configured")
    void defaults() {
        try (ConfigurableApplicationContext context = start("defaults")) {
            AuthProperties properties = context.getBean(AuthProperties.class);
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.lock().threshold()).isEqualTo(5);
            assertThat(properties.lock().duration()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.refreshTokenCleanup().retention()).isEqualTo(Duration.ofDays(7));
        }
    }

    @ParameterizedTest
    @DisplayName("out-of-range values stop the startup")
    @ValueSource(
            strings = {
                "--mastersmith.auth.lock.threshold=0",
                "--mastersmith.auth.lock.duration=0s",
                "--mastersmith.auth.access-token-ttl=0s",
                "--mastersmith.auth.password.bcrypt-cost=32"
            })
    void outOfRange(String setting) {
        assertThatThrownBy(() -> start("invalid", setting)).isInstanceOf(Exception.class);
    }
}
