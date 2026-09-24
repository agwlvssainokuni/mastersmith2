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
package cherry.mastersmith.dslmanage.web;

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.audit.service.AuditEventListener;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 監査の書き込みに失敗しても DSL の操作は成功し、アプリのログに1件出ること（AC6.3.3、NFR8.5、BR7.3）の結合テスト。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AdminAccessTestConfig.class, FailingAuditEventRepositoryConfig.class})
class DslAuditWriteFailureIT {

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
    FailingAuditEventRepository repository;

    @Autowired
    ActiveDslModelProvider activeDslModelProvider;

    @Test
    @DisplayName("apply succeeds when the audit write fails, and exactly one error without the DSL body is logged")
    void applySucceedsDespiteAuditFailure() {
        repository.mode(Mode.NONE);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        DslApi api = new DslApi(port, users.accessToken(users.createAdmin()));
        String previewId = api.submitOk(
                DslYaml.dsl().table("secret_table_name", column("c")).bytes());
        repository.mode(Mode.APPEND_FAILURE);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            int status = api.apply(previewId).statusCode();

            assertThat(status).isEqualTo(200);
            List<ILoggingEvent> errors = logs.list();
            assertThat(errors).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(String.valueOf(error.getKeyValuePairs()))
                        .contains("DSL_APPLIED")
                        .contains("actorUserId")
                        .doesNotContain("secret_table_name");
            });
        } finally {
            repository.mode(Mode.NONE);
        }
        assertThat(activeDslModelProvider.current()).isInstanceOf(ActiveDsl.Present.class);
    }
}
