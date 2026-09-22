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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TraceIdProviderTest {

    private final Tracer tracer = mock(Tracer.class);

    private final TraceIdProvider provider = new TraceIdProvider(tracer);

    private void currentSpanWithTraceId(String traceId) {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn(traceId);
    }

    @Test
    @DisplayName("returns the trace id of the current span")
    void returnsCurrentTraceId() {
        currentSpanWithTraceId("4bf92f3577b34da6a3ce929d0e0e4736");

        assertThat(provider.currentTraceId()).contains("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("returns empty when there is no current span")
    void emptyWithoutSpan() {
        when(tracer.currentSpan()).thenReturn(null);

        assertThat(provider.currentTraceId()).isEmpty();
    }

    @Test
    @DisplayName("returns empty instead of failing when the tracer throws")
    void emptyWhenTracerFails() {
        when(tracer.currentSpan()).thenThrow(new IllegalStateException("トレースの仕組みの失敗"));

        assertThat(provider.currentTraceId()).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the span has a blank trace id")
    void emptyWhenBlank() {
        currentSpanWithTraceId(" ");

        assertThat(provider.currentTraceId()).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the span has no trace id")
    void emptyWhenNull() {
        currentSpanWithTraceId(null);

        assertThat(provider.currentTraceId()).isEmpty();
    }
}
