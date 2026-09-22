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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanitizingSpanExporterTest {

    /** 受け取ったスパンを覚えておく送信の仕組み（外部へは送らない）。 */
    private static final class CapturingExporter implements SpanExporter {

        final List<SpanData> spans = new ArrayList<>();

        CompletableResultCode exportResult = CompletableResultCode.ofSuccess();

        boolean flushed;

        boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return exportResult;
        }

        @Override
        public CompletableResultCode flush() {
            flushed = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }
    }

    private final CapturingExporter capturing = new CapturingExporter();

    private SpanData record(Consumer<Span> body) {
        SanitizingSpanExporter exporter = new SanitizingSpanExporter(capturing);
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            Span span = provider.get("test").spanBuilder("request").startSpan();
            body.accept(span);
            span.end();
        }
        return capturing.spans.getLast();
    }

    @Test
    @DisplayName("exception events keep the type but drop the message and the stack trace")
    void exceptionMessageAndStackTraceRemoved() {
        SpanData span = record(s -> s.recordException(new IllegalStateException("秘密の値 secret-123")));

        EventData event = span.getEvents().getFirst();
        assertThat(event.getName()).isEqualTo("exception");
        assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.type")))
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.message")))
                .isNull();
        assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.stacktrace")))
                .isNull();
        assertThat(span.toString()).doesNotContain("secret-123");
    }

    @Test
    @DisplayName("error status keeps its code but loses the description")
    void errorStatusDescriptionRemoved() {
        SpanData span = record(s -> s.setStatus(StatusCode.ERROR, "secret-456"));

        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEmpty();
    }

    @Test
    @DisplayName("spans without exceptions are forwarded unchanged")
    void unchangedWithoutException() {
        SpanData span = record(s -> s.setAttribute("http.request.method", "GET"));

        assertThat(span.getName()).isEqualTo("request");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.request.method")))
                .isEqualTo("GET");
        assertThat(span.getEvents()).isEmpty();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    @DisplayName("exception attributes placed directly on the span are removed")
    void spanLevelExceptionAttributesRemoved() {
        SpanData span = record(s -> s.setAttribute("exception.message", "secret-789"));

        assertThat(span.getAttributes().get(AttributeKey.stringKey("exception.message")))
                .isNull();
    }

    @Test
    @DisplayName("export result, flush and shutdown are delegated to the wrapped exporter")
    void delegatesLifecycle() {
        capturing.exportResult = CompletableResultCode.ofFailure();
        SanitizingSpanExporter exporter = new SanitizingSpanExporter(capturing);

        assertThat(exporter.export(List.of()).isSuccess()).isFalse();
        assertThat(exporter.flush().isSuccess()).isTrue();
        assertThat(exporter.shutdown().isSuccess()).isTrue();
        assertThat(capturing.flushed).isTrue();
        assertThat(capturing.shutdown).isTrue();
    }
}
