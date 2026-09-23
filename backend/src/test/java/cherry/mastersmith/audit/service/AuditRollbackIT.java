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
package cherry.mastersmith.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 元の操作が取り消された場合に記録しないことの結合テスト（BR1.4、NFR10.2）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, AuditRollbackIT.RollbackConfig.class})
class AuditRollbackIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    /** U2 の更新の確定を失敗させ、元のトランザクションを取り消す。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackConfig {

        /** 確定の直前に例外を投げ、U2 の更新を取り消す受け取り。 */
        static class RollbackTrigger {

            private final AtomicBoolean failing = new AtomicBoolean(false);

            /**
             * 確定の直前に受け取り、指定されていれば例外を投げる。
             *
             * @param event 認証の出来事
             */
            @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
            public void onEvent(AuthenticationEvent event) {
                if (failing.get()) {
                    throw new IllegalStateException("テスト用の、確定を失敗させる例外");
                }
            }

            /** 以降の確定を失敗させる。 */
            void startFailing() {
                failing.set(true);
            }

            /** 確定の失敗をやめる。 */
            void stopFailing() {
                failing.set(false);
            }
        }

        /**
         * 取り消しの引き金を置く。
         *
         * @return 引き金
         */
        @Bean
        public RollbackTrigger rollbackTrigger() {
            return new RollbackTrigger();
        }
    }

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RollbackConfig.RollbackTrigger trigger;

    private AuthApi api;

    private AuditRows rows;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        rows = new AuditRows(jdbc);
        trigger.stopFailing();
        email = "rollback-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    @Test
    @DisplayName("a login whose internal update is rolled back records no audit event")
    void rolledBackLoginRecordsNothing() {
        int before = rows.count();
        trigger.startFailing();

        api.login(email, PASSWORD);

        assertThat(rows.count()).as("取り消された操作の出来事は記録しない").isEqualTo(before);
    }

    @Test
    @DisplayName("the same login records one audit event once the update commits again")
    void committedLoginRecordsAgain() {
        trigger.startFailing();
        api.login(email, PASSWORD);
        int before = rows.count();
        trigger.stopFailing();

        assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(200);

        assertThat(rows.count()).isEqualTo(before + 1);
        assertThat(rows.last().eventType()).isEqualTo("LOGIN_SUCCEEDED");
    }

    @Test
    @DisplayName("a rolled back failed login records nothing either")
    void rolledBackFailedLoginRecordsNothing() {
        int before = rows.count();
        trigger.startFailing();

        api.login(email, "まちがい");

        assertThat(rows.count()).isEqualTo(before);
    }
}
