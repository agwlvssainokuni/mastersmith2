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

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

class ErrorPathControllerTest {

    private final ErrorPathController controller = new ErrorPathController(new ErrorResponseFactory(
            new ProblemBaseUrlResolver(new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1))),
            new TraceIdProvider(mock(Tracer.class))));

    private ResponseEntity<ProblemDetail> error(Integer status, Throwable exception) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        if (exception != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        }
        return controller.error(request);
    }

    @Test
    @DisplayName("known client error statuses map to their problem types")
    void mapsKnownStatuses() {
        assertThat(ErrorPathController.toProblemType(400)).isEqualTo(CommonProblemTypes.MALFORMED_REQUEST);
        assertThat(ErrorPathController.toProblemType(404)).isEqualTo(CommonProblemTypes.NOT_FOUND);
        assertThat(ErrorPathController.toProblemType(405)).isEqualTo(CommonProblemTypes.METHOD_NOT_ALLOWED);
        assertThat(ErrorPathController.toProblemType(406)).isEqualTo(CommonProblemTypes.NOT_ACCEPTABLE);
        assertThat(ErrorPathController.toProblemType(413)).isEqualTo(CommonProblemTypes.PAYLOAD_TOO_LARGE);
        assertThat(ErrorPathController.toProblemType(415)).isEqualTo(CommonProblemTypes.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    @DisplayName("unmapped statuses become INTERNAL_ERROR")
    void unmappedStatuses() {
        assertThat(ErrorPathController.toProblemType(502)).isEqualTo(CommonProblemTypes.INTERNAL_ERROR);
        assertThat(ErrorPathController.toProblemType(418)).isEqualTo(CommonProblemTypes.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("error dispatch with a 404 status returns the NOT_FOUND error response")
    void notFoundResponse() {
        ResponseEntity<ProblemDetail> response = error(404, null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("error dispatch with an exception returns 500 without the exception message")
    void exceptionResponse() {
        ResponseEntity<ProblemDetail> response = error(500, new IllegalStateException("secret-in-message"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).doesNotContain("secret-in-message");
    }

    @Test
    @DisplayName("error dispatches of update methods return the same error response")
    void updateMethods() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 413);

        ResponseEntity<ProblemDetail> response = controller.errorForUpdate(request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("missing status attribute is treated as 500")
    void missingStatus() {
        assertThat(error(null, null).getStatusCode().value()).isEqualTo(500);
    }
}
