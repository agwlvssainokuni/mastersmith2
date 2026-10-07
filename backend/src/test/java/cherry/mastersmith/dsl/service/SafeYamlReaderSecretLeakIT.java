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
package cherry.mastersmith.dsl.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 上限つきの安全な YAML の読み込みの口を TRACE で呼んでも、入力の本文と結果の木の中身がアプリのログに出ないことの結合テスト
 * （U2 dsl-v2 の BR6.5、NFR1.11、NFR 設計の 4.1）。{@code TraceAspect} が引数と戻り値を文字列にして TRACE のログに出すため、本物の
 * Spring の部品（追跡の対象の層の {@code dsl.service}）で確かめる。
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SafeYamlReaderSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    private static final String MARKER = "LEAK_MARKER_7f3a";

    private static final SafeYamlLimits LIMITS = new SafeYamlLimits(1024, 10, 0, 1000);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    SafeYamlReader reader;

    @Autowired
    LoggingSystem loggingSystem;

    private String traced(CapturedOutput output, String yaml) {
        int offset = output.getOut().length();
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        try {
            reader.read(yaml.getBytes(StandardCharsets.UTF_8), LIMITS);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }
        return output.getOut().substring(offset) + output.getErr();
    }

    @Test
    @DisplayName("with TRACE, reading a YAML leaks neither the input body nor the parsed tree")
    void parsedResultLeaksNothing(CapturedOutput output) {
        String logs = traced(output, "roles:\n  - name: " + MARKER + "\n    label: " + MARKER + "_label\n");

        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("SafeYamlReader#read");
        assertThat(logs).contains("Parsed[nodes=");
        JsonLogRecords.assertContainsNoSecret(logs, MARKER);
        assertThat(logs).doesNotContain(MARKER);
    }

    @Test
    @DisplayName("with TRACE, a rejection leaks neither the input body nor the key named in the rejected place")
    void rejectedResultLeaksNothing(CapturedOutput output) {
        String logs = traced(output, MARKER + ": 1\n" + MARKER + ": 2\n");

        assertThat(logs).contains("SafeYamlReader#read").contains("Rejected[kind=DUPLICATE_KEY");
        JsonLogRecords.assertContainsNoSecret(logs, MARKER);
        assertThat(logs).doesNotContain(MARKER);
    }
}
