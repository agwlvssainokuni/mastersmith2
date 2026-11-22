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
package cherry.mastersmith.internaldb.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 前の版が作った内部DB（H2 のファイル）へ、接続先を変えずに起動できることの結合テスト（BR1.7・FR2.2・T6）。前の版と同じ移行
 * （{@code db/migration} の V1〜V14 だけ）で作ったファイルへ、新しい部品の入ったアプリが起動し、移行の検証が通り、前の版で作った
 * 利用者でログインできる。
 */
@ExtendWith(OutputCaptureExtension.class)
class InternalDbH2PreviousVersionIT {

    private static final String EMAIL = "previous.version@example.com";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("an H2 file created by the previous version starts unchanged and the existing user can log in")
    void startsOnAnH2FileCreatedByThePreviousVersion(CapturedOutput output) throws SQLException {
        String url = TestDatabase.url(tempDir.resolve("previous"));
        String password = "前の版-" + TestDatabase.randomSecret();
        createWithThePreviousMigrations(url, password);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + url,
                        "--server.port=0",
                        "--mastersmith.auth.password.bcrypt-cost=4")) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL",
                            Integer.class))
                    .as("the 14 applied migrations are validated and nothing is added")
                    .isEqualTo(14);
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            assertThat(new AuthApi(port).login(EMAIL, password).statusCode()).isEqualTo(200);
        }

        List<Map<String, Object>> records = JsonLogRecords.parse(output.getOut());
        assertThat(records)
                .filteredOn(record -> "Internal DB kind resolved".equals(record.get("message")))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("internalDbKind", "H2"));
        assertThat(records)
                .filteredOn(record -> "Internal DB migration finished".equals(record.get("message")))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("migrationCount", 0));
        assertThat(records)
                .filteredOn(record -> "Internal DB startup failed".equals(record.get("message")))
                .isEmpty();
    }

    /** 前の版と同じ移行（H2 の置き場）だけでファイルを作り、利用者を 1 人入れる。 */
    private static void createWithThePreviousMigrations(String url, String password) throws SQLException {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                                + " VALUES (?, ?, TRUE, ?, ?)")) {
            statement.setString(1, EMAIL);
            statement.setString(2, new BCryptPasswordEncoder(4).encode(password));
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            statement.setString(4, "前の版の利用者");
            statement.executeUpdate();
        }
    }
}
