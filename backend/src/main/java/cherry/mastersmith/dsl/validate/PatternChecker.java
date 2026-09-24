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
package cherry.mastersmith.dsl.validate;

import cherry.mastersmith.dsl.domain.DslFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 正規表現（DSL の {@code pattern}）が正しいかの確かめ（BR3.6、NFR3.6）。
 *
 * <p>正しい正規表現かを組み立て（{@link Pattern#compile}）で確かめるだけで、DSL の値に当てはめない。長さは
 * {@link DslFormat#MAX_PATTERN_LENGTH} 文字まで。組み立ては、アプリで1つの小さな実行器（スレッド 2 本、待ち行列 16 件まで）で
 * 行い、{@link DslFormat#PATTERN_CHECK_TIMEOUT} で待つのをやめて「正しくない」とする。組み立ては長さに上限があるため、待つのを
 * やめた後の組み立ても短く終わる（この前提は Build and Test で測る）。実行器が埋まって受け付けられないときも「正しくない」とする。
 *
 * <p>待ちの時間と組み立ての処理は、テストで差し替えられる。
 */
@Component
public class PatternChecker implements AutoCloseable {

    /** 確かめの結果。 */
    public enum Outcome {
        /** 正しい正規表現。 */
        VALID,
        /** 長さの上限を超えた。 */
        TOO_LONG,
        /** 正しくない、または確かめが時間の上限で打ち切られた。 */
        INVALID
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PatternChecker.class);

    private static final int THREADS = 2;

    private static final int QUEUE_CAPACITY = 16;

    private final ExecutorService executor;

    private final Duration timeout;

    private final Consumer<String> compiler;

    /** アプリの既定（待ち 100 ミリ秒、{@link Pattern#compile} で組み立てる）で作る。 */
    public PatternChecker() {
        this(newExecutor(), DslFormat.PATTERN_CHECK_TIMEOUT, Pattern::compile);
    }

    /**
     * 実行器・待ちの時間・組み立ての処理を指定して作る（テストで差し替えるために使う）。
     *
     * @param executor 実行器
     * @param timeout 待ちの時間
     * @param compiler 組み立ての処理（正しくなければ例外を投げる）
     */
    PatternChecker(ExecutorService executor, Duration timeout, Consumer<String> compiler) {
        this.executor = Objects.requireNonNull(executor, "executor は必須です");
        this.timeout = Objects.requireNonNull(timeout, "timeout は必須です");
        this.compiler = Objects.requireNonNull(compiler, "compiler は必須です");
    }

    /**
     * 正規表現を確かめる。
     *
     * @param pattern 正規表現
     * @return 確かめの結果
     */
    public Outcome check(String pattern) {
        Objects.requireNonNull(pattern, "pattern は必須です");
        if (pattern.codePointCount(0, pattern.length()) > DslFormat.MAX_PATTERN_LENGTH) {
            return Outcome.TOO_LONG;
        }
        Future<?> future;
        try {
            future = executor.submit(() -> compiler.accept(pattern));
        } catch (RejectedExecutionException e) {
            LOGGER.atWarn().addKeyValue("reason", "rejected").log("正規表現の確かめを受け付けられませんでした");
            return Outcome.INVALID;
        }
        try {
            future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return Outcome.VALID;
        } catch (ExecutionException e) {
            // 組み立ての失敗（PatternSyntaxException など）は、正しくない正規表現として扱う。部品の文言は使わない。
            return Outcome.INVALID;
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.atWarn()
                    .addKeyValue("reason", "timeout")
                    .addKeyValue("timeoutMillis", timeout.toMillis())
                    .log("正規表現の確かめを時間の上限で打ち切りました");
            return Outcome.INVALID;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return Outcome.INVALID;
        }
    }

    /** 実行器を止める。 */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static ExecutorService newExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "dsl-pattern-check-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                THREADS,
                THREADS,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                threads,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
