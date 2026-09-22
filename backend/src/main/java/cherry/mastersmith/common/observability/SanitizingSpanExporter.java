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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 外部へ送るトレースから、例外のメッセージとスタックトレースを取り除く送信の包み（BR4.3、NFR3.4、security-design 5章）。
 *
 * <p>例外の記録は型の名前（{@code exception.type}）だけを残す。スパンの状態の説明（例外のメッセージが入ることがある）も空にする。
 * 送信そのものは包んだ送信の仕組みに任せる。
 */
public class SanitizingSpanExporter implements SpanExporter {

    /** 外へ送らない属性の名前。 */
    static final Set<AttributeKey<String>> REMOVED_KEYS =
            Set.of(AttributeKey.stringKey("exception.message"), AttributeKey.stringKey("exception.stacktrace"));

    private final SpanExporter delegate;

    /**
     * 送信の仕組みを包む。
     *
     * @param delegate 包む送信の仕組み
     */
    public SanitizingSpanExporter(SpanExporter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(
                spans.stream().map(SanitizingSpanExporter::sanitize).toList());
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * 1つのスパンから秘密情報になりうる値を取り除く。
     *
     * @param span 元のスパン
     * @return 取り除いた後のスパン
     */
    static SpanData sanitize(SpanData span) {
        List<EventData> events = span.getEvents().stream()
                .map(event -> EventData.create(
                        event.getEpochNanos(),
                        event.getName(),
                        strip(event.getAttributes()),
                        event.getTotalAttributeCount()))
                .toList();
        Attributes attributes = strip(span.getAttributes());
        StatusData status = span.getStatus().getStatusCode() == StatusCode.ERROR
                ? StatusData.create(StatusCode.ERROR, "")
                : span.getStatus();
        return new DelegatingSpanData(span) {
            @Override
            public List<EventData> getEvents() {
                return events;
            }

            @Override
            public Attributes getAttributes() {
                return attributes;
            }

            @Override
            public StatusData getStatus() {
                return status;
            }
        };
    }

    private static Attributes strip(Attributes attributes) {
        if (REMOVED_KEYS.stream().noneMatch(key -> attributes.get(key) != null)) {
            return attributes;
        }
        return attributes.toBuilder()
                .removeIf(key -> REMOVED_KEYS.contains(key))
                .build();
    }
}
