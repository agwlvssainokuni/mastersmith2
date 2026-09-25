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
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * 外部へ送るログから、個人に関する値を伏せる送信の包み（260924-followup-fixes の FR1.2、FR1.3）。
 *
 * <p>ログのキーと値は属性として送られる。そのうち個人に関する値を持つキー（{@link #MASKED_KEYS}）は、キーを残して値を固定の文字列
 * {@link #MASK} に置き換える（値が文字列以外の型でも文字列の {@link #MASK} にする）。ハッシュ値にしないのは、候補の一覧から逆に
 * 引けるうえ、同じ人の記録を結び付けられるため。ほかの属性・本文・時刻・重大度・トレースの情報はそのまま送る。標準出力のログは
 * 別の経路のため変わらない。送信そのものは包んだ送信の仕組みに任せる。
 */
public class SanitizingLogRecordExporter implements LogRecordExporter {

    /** 伏せるキーの名前（メールアドレス・送り元の IP・User-Agent）。 */
    static final Set<String> MASKED_KEYS = Set.of("email", "enteredEmail", "sourceIp", "userAgent");

    /** 伏せた値の書き方。 */
    static final String MASK = "[REDACTED]";

    private final LogRecordExporter delegate;

    /**
     * 送信の仕組みを包む。
     *
     * @param delegate 包む送信の仕組み
     */
    public SanitizingLogRecordExporter(LogRecordExporter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        return delegate.export(
                logs.stream().map(SanitizingLogRecordExporter::sanitize).toList());
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
     * 1件のログの属性のうち、個人に関する値を伏せる。伏せる属性が無ければ元の記録をそのまま返す。
     *
     * @param log 元のログ
     * @return 伏せた後のログ
     */
    static LogRecordData sanitize(LogRecordData log) {
        Attributes original = log.getAttributes();
        if (original.asMap().keySet().stream().noneMatch(key -> MASKED_KEYS.contains(key.getKey()))) {
            return log;
        }
        AttributesBuilder builder = original.toBuilder().removeIf(key -> MASKED_KEYS.contains(key.getKey()));
        original.forEach((key, value) -> {
            if (MASKED_KEYS.contains(key.getKey())) {
                builder.put(AttributeKey.stringKey(key.getKey()), MASK);
            }
        });
        return new MaskedLogRecordData(log, builder.build());
    }

    /** 属性だけを差し替え、ほかの取り出し口はすべて元の記録に任せるログ。 */
    private record MaskedLogRecordData(LogRecordData original, Attributes attributes) implements LogRecordData {

        @Override
        public Resource getResource() {
            return original.getResource();
        }

        @Override
        public InstrumentationScopeInfo getInstrumentationScopeInfo() {
            return original.getInstrumentationScopeInfo();
        }

        @Override
        public long getTimestampEpochNanos() {
            return original.getTimestampEpochNanos();
        }

        @Override
        public long getObservedTimestampEpochNanos() {
            return original.getObservedTimestampEpochNanos();
        }

        @Override
        public SpanContext getSpanContext() {
            return original.getSpanContext();
        }

        @Override
        public Severity getSeverity() {
            return original.getSeverity();
        }

        @Override
        public String getSeverityText() {
            return original.getSeverityText();
        }

        @Override
        @SuppressWarnings("deprecation")
        public Body getBody() {
            return original.getBody();
        }

        @Override
        public Value<?> getBodyValue() {
            return original.getBodyValue();
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }

        @Override
        public int getTotalAttributeCount() {
            return original.getTotalAttributeCount();
        }

        @Override
        public String getEventName() {
            return original.getEventName();
        }
    }
}
