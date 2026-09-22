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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 例外の変換の1か所を、Spring を起動しない MockMvc で確かめる。 */
class GlobalExceptionHandlerTest {

    private static final ProblemType CONFLICT = new ProblemType(
            "ITEM_CONFLICT",
            409,
            new LocalizedText("競合", "Conflict"),
            new LocalizedText("競合しました。", "A conflict occurred."),
            null);

    /**
     * テスト用の窓口。MockMvc へ直接渡す。アプリを起動するほかのテストの部品の走査で読み込まれないよう、設定しない値を条件にする。
     */
    @RestController
    @ConditionalOnBooleanProperty("mastersmith.test-fixture.standalone-only")
    static class Endpoints {

        record Item(@NotBlank String name) {}

        @PostMapping(path = "/api/items", consumes = MediaType.APPLICATION_JSON_VALUE)
        String create(@Valid @RequestBody Item item) {
            return item.name();
        }

        @GetMapping(path = "/api/items", produces = MediaType.APPLICATION_JSON_VALUE)
        int list(@RequestParam int page) {
            return page;
        }

        @GetMapping("/api/conflict")
        String conflict() {
            throw new BusinessException(CONFLICT, "表示してよい説明");
        }

        @GetMapping("/api/boom")
        String boom() {
            throw new IllegalStateException("内部の情報 secret-value-123");
        }

        @GetMapping("/api/missing")
        String missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/missing", "api/missing");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory(
                    new ProblemBaseUrlResolver(new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1))),
                    new TraceIdProvider(mock(Tracer.class)))))
            .build();

    private ResultActions perform(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    private static void expectProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("validation failure becomes 400 VALIDATION_FAILED")
    void validationFailure() throws Exception {
        expectProblem(
                perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}")),
                400,
                "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("missing and mistyped request parameters become 400 VALIDATION_FAILED")
    void parameterProblems() throws Exception {
        expectProblem(perform(get("/api/items")), 400, "VALIDATION_FAILED");
        expectProblem(perform(get("/api/items").param("page", "abc")), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("business exception becomes its own problem type with the user-facing detail")
    void businessException() throws Exception {
        ResultActions result = perform(get("/api/conflict"));

        expectProblem(result, 409, "ITEM_CONFLICT");
        result.andExpect(jsonPath("$.detail").value("表示してよい説明"))
                .andExpect(jsonPath("$.type").value("http://localhost/api/problems/item-conflict"));
    }

    @Test
    @DisplayName("unexpected exception becomes 500 INTERNAL_ERROR without the exception message")
    void unexpectedException() throws Exception {
        ResultActions result = perform(get("/api/boom"));

        expectProblem(result, 500, "INTERNAL_ERROR");
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-value-123").doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("framework 4xx exceptions keep their status with dedicated codes")
    void frameworkClientErrors() throws Exception {
        ResultActions methodNotAllowed =
                perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/items"));
        expectProblem(methodNotAllowed, 405, "METHOD_NOT_ALLOWED");
        methodNotAllowed.andExpect(header().exists("Allow"));

        expectProblem(
                perform(post("/api/items").contentType(MediaType.TEXT_PLAIN).content("x")),
                415,
                "UNSUPPORTED_MEDIA_TYPE");
        expectProblem(
                perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":")),
                400,
                "MALFORMED_REQUEST");
        expectProblem(
                perform(get("/api/items").param("page", "1").accept(MediaType.TEXT_PLAIN)), 406, "NOT_ACCEPTABLE");
        expectProblem(perform(get("/api/missing")), 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("4xx is logged once at WARN without a stack trace")
    void clientErrorLogging() throws Exception {
        try (LogEvents events = LogEvents.capture(GlobalExceptionHandler.class)) {
            perform(get("/api/conflict"));

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).hasSize(1);
            assertThat(logged.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFirst().getThrowableProxy()).isNull();
            assertThat(logged.getFirst().getKeyValuePairs()).anySatisfy(pair -> {
                assertThat(pair.key).isEqualTo("code");
                assertThat(pair.value).isEqualTo("ITEM_CONFLICT");
            });
        }
    }

    @Test
    @DisplayName("5xx is logged once at ERROR with the stack trace")
    void serverErrorLogging() throws Exception {
        try (LogEvents events = LogEvents.capture(GlobalExceptionHandler.class)) {
            perform(get("/api/boom"));

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).hasSize(1);
            assertThat(logged.getFirst().getLevel()).isEqualTo(Level.ERROR);
            assertThat(logged.getFirst().getThrowableProxy()).isNotNull();
            assertThat(logged.getFirst().getThrowableProxy().getClassName())
                    .isEqualTo(IllegalStateException.class.getName());
        }
    }
}
