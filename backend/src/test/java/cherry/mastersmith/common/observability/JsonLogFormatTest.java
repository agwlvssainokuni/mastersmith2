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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** logback-spring.xml の出力の形（FR10.1、BR3.1、BR3.2、BR3.5、NFR3.11、NFR10.2）を、Spring を起動せずに確かめる。 */
class JsonLogFormatTest {

    private LoggerContext context;

    private final List<String> lines = new ArrayList<>();

    private Logger logger;

    @BeforeEach
    void configure() throws Exception {
        context = new LoggerContext();
        // SLF4J の MDC（アプリと同じもの）を、このテスト用のログの仕組みでも使う。
        context.setMDCAdapter(MDC.getMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        @SuppressWarnings("unchecked")
        OutputStreamAppender<ILoggingEvent> console =
                (OutputStreamAppender<ILoggingEvent>) root.getAppender("JSON_STDOUT");
        Encoder<ILoggingEvent> encoder = console.getEncoder();
        root.detachAppender(console);
        AppenderBase<ILoggingEvent> capture = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
            }
        };
        capture.setContext(context);
        capture.start();
        root.addAppender(capture);
        logger = context.getLogger("cherry.mastersmith.sample.SampleService");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        context.stop();
    }

    private Map<String, Object> single() {
        assertThat(lines).hasSize(1);
        String line = lines.getFirst();
        assertThat(line).endsWith("\n");
        assertThat(line.substring(0, line.length() - 1)).doesNotContain("\n");
        List<Map<String, Object>> records = JsonLogRecords.parse(line);
        assertThat(records).hasSize(1);
        return records.getFirst();
    }

    @Test
    @DisplayName("one event is written as one line of JSON with the mandatory fields")
    void mandatoryFields() {
        logger.info("起動しました");

        Map<String, Object> record = single();
        assertThat(record)
                .containsEntry("level", "INFO")
                .containsEntry("logger", "cherry.mastersmith.sample.SampleService")
                .containsEntry("message", "起動しました")
                .containsKeys("timestamp", "thread");
    }

    @Test
    @DisplayName("timestamp is ISO 8601 with a time zone offset")
    void timestampHasOffset() {
        logger.info("時刻");

        assertThat(single().get("timestamp"))
                .asString()
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})");
    }

    @Test
    @DisplayName("traceId and spanId appear only when present in the MDC and other MDC values are left out")
    void onlyTraceIdsFromMdc() {
        logger.info("トレースなし");
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put("spanId", "00f067aa0ba902b7");
        MDC.put("userEmail", "user@example.com");
        logger.info("トレースあり");

        List<Map<String, Object>> records = JsonLogRecords.parse(String.join("", lines));
        assertThat(records.get(0)).doesNotContainKeys("traceId", "spanId");
        assertThat(records.get(1))
                .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry("spanId", "00f067aa0ba902b7")
                .doesNotContainKey("userEmail");
        assertThat(String.join("", lines)).doesNotContain("user@example.com");
    }

    @Test
    @DisplayName("key-value pairs are written as JSON values")
    void keyValuePairs() {
        logger.atInfo()
                .addKeyValue("code", "NOT_FOUND")
                .addKeyValue("status", 404)
                .log("変換しました");

        assertThat(single()).containsEntry("code", "NOT_FOUND").containsEntry("status", 404);
    }

    @Test
    @DisplayName("values containing line breaks stay on one line and cannot forge another record")
    void lineBreaksEscaped() {
        logger.atWarn()
                .addKeyValue("input", "a\n{\"level\":\"ERROR\",\"message\":\"forged\"}")
                .log("改行を含む\n値");

        Map<String, Object> record = single();
        assertThat(record).containsEntry("message", "改行を含む\n値");
        assertThat(record.get("input")).asString().contains("forged");
    }

    @Test
    @DisplayName("exception is written in the exception field only when present")
    void exceptionField() {
        logger.info("例外なし");
        logger.error("例外あり", new IllegalStateException("失敗"));

        List<Map<String, Object>> records = JsonLogRecords.parse(String.join("", lines));
        assertThat(records.get(0)).doesNotContainKey("exception");
        assertThat(records.get(1).get("exception"))
                .asString()
                .contains("IllegalStateException")
                .contains("失敗")
                .contains("at ");
    }

    @Test
    @DisplayName("caller location and host name are not written")
    void noCallerOrHost() {
        logger.info("場所");

        assertThat(single()).containsOnlyKeys("timestamp", "level", "logger", "thread", "message");
    }
}
