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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanitizingLogRecordExporterTest {

    /** 受け取ったログを覚えておく送信の仕組み（外部へは送らない）。 */
    private static final class CapturingExporter implements LogRecordExporter {

        final List<LogRecordData> logs = new ArrayList<>();

        CompletableResultCode exportResult = CompletableResultCode.ofSuccess();

        boolean flushed;

        boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<LogRecordData> batch) {
            logs.addAll(batch);
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

    private static final SpanContext SPAN_CONTEXT = SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", TraceFlags.getSampled(), TraceState.getDefault());

    private final CapturingExporter capturing = new CapturingExporter();

    /** 本物の SDK で1件のログを作り、包んだ送信の仕組みを通して、元の送信の仕組みが受け取った記録を返す。 */
    private LogRecordData record(Consumer<LogRecordBuilder> body) {
        SanitizingLogRecordExporter exporter = new SanitizingLogRecordExporter(capturing);
        try (SdkLoggerProvider provider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()) {
            LogRecordBuilder builder = provider.get("test")
                    .logRecordBuilder()
                    .setContext(Context.root().with(Span.wrap(SPAN_CONTEXT)))
                    .setTimestamp(1_000L, TimeUnit.NANOSECONDS)
                    .setSeverity(Severity.ERROR)
                    .setSeverityText("ERROR")
                    .setBody("監査の記録に失敗しました");
            body.accept(builder);
            builder.emit();
        }
        return capturing.logs.getLast();
    }

    @Test
    @DisplayName("personal values are replaced while the keys stay")
    void personalValuesMasked() {
        LogRecordData log = record(b -> b.setAttribute(AttributeKey.stringKey("email"), "admin@example.com")
                .setAttribute(AttributeKey.stringKey("enteredEmail"), "user@example.com")
                .setAttribute(AttributeKey.stringKey("sourceIp"), "198.51.100.7")
                .setAttribute(AttributeKey.stringKey("userAgent"), "テスト用のブラウザ")
                .setAttribute(AttributeKey.longKey("userAgent"), 42L));

        Attributes attributes = log.getAttributes();
        for (String key : List.of("email", "enteredEmail", "sourceIp", "userAgent")) {
            assertThat(attributes.get(AttributeKey.stringKey(key))).as(key).isEqualTo("[REDACTED]");
        }
        // 文字列以外の型の値も、元の型の属性を消して文字列の [REDACTED] にする。
        assertThat(attributes.get(AttributeKey.longKey("userAgent"))).isNull();
        assertThat(attributes.size()).isEqualTo(4);
        assertThat(attributes.toString())
                .doesNotContain("admin@example.com", "user@example.com", "198.51.100.7", "テスト用のブラウザ");
    }

    @Test
    @DisplayName("a value of another type under a masked key is masked as a string")
    void nonStringValueMasked() {
        LogRecordData log = record(b -> b.setAttribute(AttributeKey.longKey("sourceIp"), 3_325_256_711L));

        assertThat(log.getAttributes().get(AttributeKey.longKey("sourceIp"))).isNull();
        assertThat(log.getAttributes().get(AttributeKey.stringKey("sourceIp"))).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("other attributes, the body and the trace context are kept")
    void othersKept() {
        LogRecordData log = record(b -> b.setAttribute(AttributeKey.stringKey("dsl.operation"), "APPLY")
                .setAttribute(AttributeKey.longKey("userId"), 7L)
                .setAttribute(AttributeKey.stringKey("email"), "admin@example.com"));

        assertThat(log.getAttributes().get(AttributeKey.stringKey("dsl.operation")))
                .isEqualTo("APPLY");
        assertThat(log.getAttributes().get(AttributeKey.longKey("userId"))).isEqualTo(7L);
        assertThat(log.getBodyValue().asString()).isEqualTo("監査の記録に失敗しました");
        assertThat(log.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(log.getSeverityText()).isEqualTo("ERROR");
        assertThat(log.getTimestampEpochNanos()).isEqualTo(1_000L);
        assertThat(log.getObservedTimestampEpochNanos()).isPositive();
        assertThat(log.getSpanContext()).isEqualTo(SPAN_CONTEXT);
        assertThat(log.getInstrumentationScopeInfo().getName()).isEqualTo("test");
        assertThat(log.getResource()).isNotNull();
        assertThat(log.getTotalAttributeCount()).isEqualTo(3);
        assertThat(log.getEventName()).isNull();
    }

    @Test
    @DisplayName("a log without masked keys is forwarded as the same record")
    void unchangedWithoutMaskedKeys() {
        SanitizingLogRecordExporter exporter = new SanitizingLogRecordExporter(capturing);
        LogRecordData original = record(b -> b.setAttribute(AttributeKey.stringKey("code"), "NOT_FOUND"));

        assertThat(SanitizingLogRecordExporter.sanitize(original)).isSameAs(original);
        assertThat(exporter.export(List.of(original)).isSuccess()).isTrue();
        assertThat(capturing.logs.getLast()).isSameAs(original);
    }

    @Test
    @DisplayName("export result, flush, shutdown and close are delegated to the wrapped exporter")
    void delegatesLifecycle() {
        capturing.exportResult = CompletableResultCode.ofFailure();
        SanitizingLogRecordExporter exporter = new SanitizingLogRecordExporter(capturing);

        assertThat(exporter.export(List.of()).isSuccess()).isFalse();
        assertThat(exporter.flush().isSuccess()).isTrue();
        assertThat(exporter.shutdown().isSuccess()).isTrue();
        assertThat(capturing.flushed).isTrue();
        assertThat(capturing.shutdown).isTrue();
        capturing.shutdown = false;
        exporter.close();
        assertThat(capturing.shutdown).isTrue();
    }
}
