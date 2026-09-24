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
package cherry.mastersmith.targetdb.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 対象DB の接続先・資格情報がログに出ないことの結合テスト（US6.1 の AC6.1.4、BR1.4、NFR4.2）。
 *
 * <p>{@code cherry.mastersmith} のロガーを TRACE にしてメソッドの呼び出しの追跡（TraceAspect）を有効にし、引数・戻り値まで
 * 出る状態で、設定の型の文字列化と読み取りの呼び出し（失敗する）を確かめる。対象DB は届かない先（手元の閉じた番号）にする
 * （BR1.9、NFR4.4 も合わせて確かめる）。
 */
@ExtendWith(OutputCaptureExtension.class)
class TargetDbSecretLeakIT {

    @TempDir
    Path tempDir;

    private ConfigurableApplicationContext start(String host, int port, String username, String password) {
        return new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(tempDir),
                        "--server.port=0",
                        "--logging.level.cherry.mastersmith=TRACE",
                        "--mastersmith.target-db.type=postgresql",
                        "--mastersmith.target-db.host=" + host,
                        "--mastersmith.target-db.port=" + port,
                        "--mastersmith.target-db.database=business",
                        "--mastersmith.target-db.schema=sales",
                        "--mastersmith.target-db.username=" + username,
                        "--mastersmith.target-db.password=" + password);
    }

    @Test
    @DisplayName(
            "with TRACE enabled neither the settings text nor a failed read logs the password or the connection target")
    void passwordNeverLogged(CapturedOutput output) throws Exception {
        String password = TestDatabase.randomSecret();
        String username = "reader" + TestDatabase.randomSecret();
        int port = TargetDbStartupIT.closedPort();
        TargetSchemaResult result;
        try (ConfigurableApplicationContext context = start("127.0.0.1", port, username, password)) {
            TargetDbProperties properties = context.getBean(TargetDbProperties.class);
            TargetDbSettings settings = context.getBean(TargetDbSettings.class);

            assertThat(properties.password()).as("設定は読めている").isEqualTo(password);
            assertThat(properties.toString()).doesNotContain(password).doesNotContain(username);
            assertThat(settings.toString()).doesNotContain(password).doesNotContain(username);

            result = context.getBean(TargetSchemaReader.class).readSchema(ReadPurpose.GENERATE);
        }
        assertThat(result).isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED));
        assertThat(output.getOut())
                .as("TRACE のメソッドの追跡が、読み取りの呼び出しの出入りを出している")
                .contains("ENTER JdbcTargetSchemaReader#readSchema(GENERATE)")
                .contains("EXIT  JdbcTargetSchemaReader#readSchema(): Unavailable[reason=CONNECTION_FAILED]");
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password, username, "127.0.0.1:" + port);
    }
}
