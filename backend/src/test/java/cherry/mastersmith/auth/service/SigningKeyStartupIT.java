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
import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/** 署名鍵が無い・短いときに起動が止まり、出力に鍵の値が出ないことの結合テスト（NFR3.3、NFR6.2）。 */
@ExtendWith(OutputCaptureExtension.class)
class SigningKeyStartupIT {

    @TempDir
    Path tempDir;

    private ConfigurableApplicationContext start(String... args) {
        String[] all = new String[args.length + 3];
        all[0] = "--spring.datasource.url=" + TestDatabase.url(tempDir.resolve("db"));
        all[1] = "--server.port=0";
        all[2] = "--" + TestSigningKeyEnvironmentPostProcessor.INJECTION + "=false";
        System.arraycopy(args, 0, all, 3, args.length);
        return new SpringApplicationBuilder(MastersmithApplication.class).run(all);
    }

    @Test
    @DisplayName("the application does not start without a signing key")
    void missingKey() {
        assertThatThrownBy(this::start)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mastersmith.auth.signing-key");
    }

    @Test
    @DisplayName("the application does not start with a 31-byte key and the key never appears in the output")
    void shortKey(CapturedOutput output) {
        String key = TestSigningKeyEnvironmentPostProcessor.randomKey(31);

        assertThatThrownBy(() -> start("--mastersmith.auth.signing-key=" + key))
                .rootCause()
                .hasMessageContaining("32 バイト");
        JsonLogRecords.assertContainsNoSecret(output.getAll(), key);
    }

    @Test
    @DisplayName("the application starts with a 32-byte key and the key never appears in the output")
    void validKey(CapturedOutput output) {
        String key = TestSigningKeyEnvironmentPostProcessor.randomKey(32);

        try (ConfigurableApplicationContext context = start("--mastersmith.auth.signing-key=" + key)) {
            assertThat(context.isRunning()).isTrue();
        }
        JsonLogRecords.assertContainsNoSecret(output.getAll(), key);
    }
}
