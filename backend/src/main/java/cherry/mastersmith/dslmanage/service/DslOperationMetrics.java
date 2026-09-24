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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.dslmanage.domain.DslDownload;
import cherry.mastersmith.dslmanage.domain.DslSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DSL の操作の指標とログ（NFR1.16・NFR1.17）。
 *
 * <p>操作の終わりで1回、Timer {@code mastersmith.dsl.operation}（タグ {@code operation}・{@code outcome}、95 パーセンタイルを
 * 求められるよう分布のヒストグラムを出す）に記録し、INFO のログを1件、キーと値で出す（{@code dsl.operation}・{@code dsl.outcome}・
 * {@code dsl.durationMs}・{@code dsl.hash}（先頭12文字）・{@code dsl.source}）。タグとログに、DSL の本文・利用者・接続先・部品の
 * 例外の文言を入れない。
 */
@Component
public class DslOperationMetrics {

    /** 指標の名前。 */
    public static final String METRIC = "mastersmith.dsl.operation";

    /** INFO のログの文言。 */
    static final String LOG_MESSAGE = "DSL の操作を終えました";

    private static final Logger LOGGER = LoggerFactory.getLogger(DslOperationMetrics.class);

    private final MeterRegistry registry;

    /**
     * 作る。
     *
     * @param registry 指標の登録先
     */
    public DslOperationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 始まりの時刻を返す（{@link #record} に渡す）。
     *
     * @return 始まりの時刻（ナノ秒。経過時間の計算だけに使う）
     */
    public long start() {
        return System.nanoTime();
    }

    /**
     * 操作の終わりを記録する。
     *
     * @param operation 操作
     * @param outcome 結果
     * @param startNanos {@link #start()} の値
     * @param dslHash DSL の識別（無ければ null。ログには先頭12文字だけ出す）
     * @param source 出どころ（無ければ null）
     */
    public void record(DslOperation operation, DslOutcome outcome, long startNanos, String dslHash, DslSource source) {
        long elapsed = Math.max(0, System.nanoTime() - startNanos);
        Timer.builder(METRIC)
                .description("DSL の操作の時間と件数")
                .tag("operation", operation.tag())
                .tag("outcome", outcome.tag())
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofNanos(elapsed));
        LOGGER.atInfo()
                .addKeyValue("dsl.operation", operation.tag())
                .addKeyValue("dsl.outcome", outcome.tag())
                .addKeyValue("dsl.durationMs", TimeUnit.NANOSECONDS.toMillis(elapsed))
                .addKeyValue("dsl.hash", DslDownload.prefix(dslHash))
                .addKeyValue("dsl.source", source == null ? null : source.name())
                .log(LOG_MESSAGE);
    }
}
