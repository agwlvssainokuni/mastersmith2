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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 要求中のトレースIDを返す（functional-spec 6.1）。U1 のエラー応答と、U4 の監査ログが使う。
 *
 * <p>トレースIDは自前で作らず、分散トレースの仕組み（Micrometer Tracing）が要求ごとに割り当てた値を参照する。現在のスパンが
 * 無い場合（要求の外で呼ばれた場合など）は「無し」を返し、処理を失敗させない。
 */
@Component
public class TraceIdProvider {

    private final Tracer tracer;

    /**
     * トレースIDの参照の仕組みを作る。
     *
     * @param tracer 分散トレースの仕組み
     */
    public TraceIdProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 現在のスパンのトレースIDを返す。
     *
     * @return トレースID（現在のスパンが無い、または取得できなければ空）
     */
    public Optional<String> currentTraceId() {
        try {
            Span span = tracer.currentSpan();
            if (span == null) {
                return Optional.empty();
            }
            String traceId = span.context().traceId();
            return traceId == null || traceId.isBlank() ? Optional.empty() : Optional.of(traceId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
