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

import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

/**
 * 値を出力に出さない確かめ（NFR 設計 DD-12。計画の Step 9）。アプリの起動から初期管理者のログインまでの出力に、データベースの利用者・パスワード・初期管理者の
 * 資格情報の文字列が無い（資格情報を含まない接続先の URL は Flyway の INFO に出るため、合否に入れず観測として出す）。BR2・BR3・BR5 のクラス（{@code PgViolationClassificationIT}・{@code PgWriteWaitIT}・{@code PgKeyConflictIT}）には、
 * 作った値がアプリのログに出ない確かめを 1 件ずつ足した（DD-13）。
 */
@InternalDbPostgresIT
class PgSecretLeakIT {

    @Test
    @DisplayName("the startup and the first login print no database user, password or initial admin credential")
    void startupOutputHasNoConnectionOrCredential(CapturedOutput output) {
        int logStart = output.getAll().length();
        String jdbcUrl;
        String user;
        String password;
        String adminEmail;
        String adminPassword;
        try (PgApp app = PgApp.start()) {
            InternalDbPostgresDatabase database = app.database();
            jdbcUrl = database.jdbcUrl();
            user = database.user();
            password = database.password();
            adminEmail = app.adminEmail();
            adminPassword = app.adminPassword();
            PortableFixture fixture = new PortableFixture(app);
            assertThat(fixture.loginResponse(adminEmail, adminPassword).statusCode())
                    .isEqualTo(200);
        }

        String printed = output.getAll().substring(logStart);
        assertThat(printed).as("the startup output is not empty").isNotBlank();
        List<String> leaked = new ArrayList<>();
        // 資格情報を含まない接続先の URL は、Flyway の起動の INFO に出る（project.md の Forbidden は資格情報を含む URL を禁じる）。観測として出す。
        System.out.println("INTERNAL_DB_PG_OBSERVE startupOutputContainsConnectionUrl=" + printed.contains(jdbcUrl));
        if (printed.contains(user)) {
            leaked.add("database user");
        }
        if (printed.contains(password)) {
            leaked.add("database password");
        }
        if (printed.contains(adminPassword)) {
            leaked.add("initial admin password");
        }
        if (printed.contains(adminEmail)) {
            leaked.add("initial admin email");
        }
        assertThat(leaked).as("values found in the output (names only)").isEmpty();
    }
}
