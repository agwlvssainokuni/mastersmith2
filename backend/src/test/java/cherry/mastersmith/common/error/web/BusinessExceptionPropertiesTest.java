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
package cherry.mastersmith.common.error.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import io.micrometer.tracing.Tracer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 業務エラーの追加の項目（BR8.1）と、想定内の 5xx のログ（NFR5.3）の単体テスト（Intent 260923-dsl-schema-loader の U4）。 */
class BusinessExceptionPropertiesTest {

    private static final ProblemType INVALID = new ProblemType(
            "ITEM_INVALID",
            422,
            new LocalizedText("不正", "Invalid"),
            new LocalizedText("不正です。", "It is invalid."),
            null);

    private static final ProblemType BUSY = new ProblemType(
            "ITEM_BUSY", 503, new LocalizedText("処理中", "Busy"), new LocalizedText("処理中です。", "It is busy."), null);

    /** テスト用の窓口（ほかのテストの部品の走査で読み込まれないよう、設定しない値を条件にする）。 */
    @RestController
    @ConditionalOnBooleanProperty("mastersmith.test-fixture.standalone-only")
    static class Endpoints {

        @GetMapping("/api/invalid")
        String invalid() {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("total", 2);
            extra.put("errors", List.of(Map.of("message", "一つ目"), Map.of("message", "二つ目")));
            extra.put("code", "OVERRIDDEN");
            extra.put("status", 999);
            extra.put("traceId", "forged");
            throw new BusinessException(INVALID, null, extra);
        }

        @GetMapping("/api/busy")
        String busy() {
            throw new BusinessException(BUSY);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory(
                    new ProblemBaseUrlResolver(new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1))),
                    new TraceIdProvider(mock(Tracer.class)))))
            .build();

    @Test
    @DisplayName("extra properties of a business exception are added without overriding the existing fields")
    void extraPropertiesAreAdded() throws Exception {
        mockMvc.perform(get("/api/invalid"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ITEM_INVALID"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.errors[1].message").value("二つ目"));
    }

    @Test
    @DisplayName("an expected business failure with 503 is logged once at WARN without a stack trace")
    void expectedServerStatusIsWarn() throws Exception {
        try (LogEvents events = LogEvents.capture(GlobalExceptionHandler.class)) {
            mockMvc.perform(get("/api/busy")).andExpect(status().is(503));

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
    }

    @Test
    @DisplayName("a business exception without extra properties has an empty, unmodifiable map")
    void noExtraProperties() {
        BusinessException exception = new BusinessException(BUSY, "説明");

        assertThat(exception.getProperties()).isEmpty();
        assertThat(exception.getDetail()).isEqualTo("説明");
    }
}
