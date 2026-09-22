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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class DefaultErrorResponseWriterTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    private final ErrorResponseFactory factory = new ErrorResponseFactory(
            new ProblemBaseUrlResolver(new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1))),
            new TraceIdProvider(tracer()));

    private static Tracer tracer() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn(TRACE_ID);
        return tracer;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.setServerName("localhost");
        request.setServerPort(8080);
        return request;
    }

    private MockHttpServletResponse write(List<HttpMessageConverter<?>> converters) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultErrorResponseWriter(factory, () -> converters)
                .write(request(), response, CommonProblemTypes.PAYLOAD_TOO_LARGE);
        return response;
    }

    private static Map<String, Object> body(MockHttpServletResponse response) {
        String json = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        return JsonMapper.builder().build().readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Test
    @DisplayName("writes the status and the problem+json content type")
    void writesStatusAndContentType() throws Exception {
        MockHttpServletResponse response = write(List.of(new JacksonJsonHttpMessageConverter()));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(MediaType.parseMediaType(response.getContentType())
                        .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
    }

    @Test
    @DisplayName("body has the same fields as the error response built by the factory")
    void bodyMatchesFactory() throws Exception {
        Map<String, Object> body = body(write(List.of(new JacksonJsonHttpMessageConverter())));

        assertThat(body)
                .containsEntry("type", "http://localhost:8080/api/problems/payload-too-large")
                .containsEntry("title", "要求が大きすぎます")
                .containsEntry("status", 413)
                .containsEntry("instance", "/api/upload")
                .containsEntry("code", "PAYLOAD_TOO_LARGE")
                .containsEntry("traceId", TRACE_ID)
                .containsKey("detail");
    }

    @Test
    @DisplayName("Japanese text is written in UTF-8")
    void writesUtf8() throws Exception {
        MockHttpServletResponse response = write(List.of(new JacksonJsonHttpMessageConverter()));

        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("要求が大きすぎます");
    }

    @Test
    @DisplayName("the first converter able to write problem+json is used")
    void skipsIncompatibleConverters() throws Exception {
        MockHttpServletResponse response =
                write(List.of(new StringHttpMessageConverter(), new JacksonJsonHttpMessageConverter()));

        assertThat(body(response)).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("fails when no converter can write problem+json")
    void failsWithoutConverter() {
        assertThatThrownBy(() -> write(List.of(new StringHttpMessageConverter())))
                .isInstanceOf(IllegalStateException.class);
    }
}
