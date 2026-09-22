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
import static org.mockito.Mockito.when;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

class ErrorResponseFactoryTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static ErrorResponseFactory factory(String traceId) {
        Tracer tracer = mock(Tracer.class);
        if (traceId != null) {
            Span span = mock(Span.class);
            TraceContext context = mock(TraceContext.class);
            when(tracer.currentSpan()).thenReturn(span);
            when(span.context()).thenReturn(context);
            when(context.traceId()).thenReturn(traceId);
        }
        ProblemBaseUrlResolver resolver =
                new ProblemBaseUrlResolver(new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1)));
        return new ErrorResponseFactory(resolver, new TraceIdProvider(tracer));
    }

    private static MockHttpServletRequest request(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items/1");
        request.setServerName("localhost");
        request.setServerPort(8080);
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        return request;
    }

    @Test
    @DisplayName("fills type, title, status, detail, instance, code and traceId")
    void fillsAllFields() {
        ProblemDetail problem = factory(TRACE_ID).create(request(null), CommonProblemTypes.NOT_FOUND, null);

        assertThat(problem.getType()).isEqualTo(URI.create("http://localhost:8080/api/problems/not-found"));
        assertThat(problem.getTitle()).isEqualTo("見つかりません");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail())
                .isEqualTo(CommonProblemTypes.NOT_FOUND.description().ja());
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/items/1"));
        assertThat(problem.getProperties()).containsEntry("code", "NOT_FOUND").containsEntry("traceId", TRACE_ID);
    }

    @Test
    @DisplayName("title and default detail follow the Accept-Language of the request")
    void followsDisplayLanguage() {
        ProblemDetail problem =
                factory(TRACE_ID).create(request("en-US,en;q=0.9"), CommonProblemTypes.INTERNAL_ERROR, null);

        assertThat(problem.getTitle()).isEqualTo("Internal server error");
        assertThat(problem.getDetail())
                .isEqualTo(CommonProblemTypes.INTERNAL_ERROR.description().en());
    }

    @Test
    @DisplayName("user-facing detail given by the caller is used as is")
    void usesGivenDetail() {
        ProblemDetail problem =
                factory(TRACE_ID).create(request(null), CommonProblemTypes.VALIDATION_FAILED, "表示してよい説明");

        assertThat(problem.getDetail()).isEqualTo("表示してよい説明");
    }

    @Test
    @DisplayName("traceId is omitted instead of failing when no span is active")
    void noTraceIdWithoutSpan() {
        ProblemDetail problem = factory(null).create(request(null), CommonProblemTypes.NOT_FOUND, null);

        assertThat(problem.getProperties()).containsEntry("code", "NOT_FOUND").doesNotContainKey("traceId");
    }

    @Test
    @DisplayName("an instance that is not a valid URI is left out")
    void invalidInstanceOmitted() {
        MockHttpServletRequest request = request(null);
        request.setRequestURI("/api/a b|c");

        ProblemDetail problem = factory(TRACE_ID).create(request, CommonProblemTypes.NOT_FOUND, null);

        assertThat(problem.getInstance()).isNull();
    }

    @Test
    @DisplayName("blank detail falls back to the description of the problem type")
    void blankDetailFallsBack() {
        ProblemDetail problem = factory(TRACE_ID).create(request(null), CommonProblemTypes.PAYLOAD_TOO_LARGE, "  ");

        assertThat(problem.getDetail())
                .isEqualTo(CommonProblemTypes.PAYLOAD_TOO_LARGE.description().ja());
    }
}
