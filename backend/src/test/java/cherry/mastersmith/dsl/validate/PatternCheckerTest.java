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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 正規表現の確かめの単体テスト（BR3.6、NFR3.6）。 */
class PatternCheckerTest {

    @Test
    @DisplayName("valid and invalid regular expressions are told apart by compiling only")
    void validAndInvalid() {
        try (PatternChecker checker = new PatternChecker()) {
            assertThat(checker.check("^[0-9]{3}-[0-9]{4}$")).isEqualTo(PatternChecker.Outcome.VALID);
            assertThat(checker.check("(unclosed")).isEqualTo(PatternChecker.Outcome.INVALID);
            assertThat(checker.check("a{2,1}")).isEqualTo(PatternChecker.Outcome.INVALID);
        }
    }

    @Test
    @DisplayName("1,000 characters are checked and 1,001 are too long, counting code points")
    void lengthBoundary() {
        try (PatternChecker checker = new PatternChecker()) {
            assertThat(checker.check("a".repeat(1000))).isEqualTo(PatternChecker.Outcome.VALID);
            assertThat(checker.check("😀".repeat(1000))).isEqualTo(PatternChecker.Outcome.VALID);
            assertThat(checker.check("a".repeat(1001))).isEqualTo(PatternChecker.Outcome.TOO_LONG);
        }
    }

    @Test
    @DisplayName("a compilation that does not finish in time is given up and treated as invalid")
    void timeoutIsInvalid() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (PatternChecker checker = new PatternChecker(executor, Duration.ofMillis(50), pattern -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            assertThat(checker.check("(a+)+$")).isEqualTo(PatternChecker.Outcome.INVALID);
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("a check that the executor cannot accept is treated as invalid")
    void rejectedIsInvalid() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        PatternChecker checker = new PatternChecker(executor, Duration.ofMillis(100), Pattern::compile);

        assertThat(checker.check("abc")).isEqualTo(PatternChecker.Outcome.INVALID);
    }

    @Test
    @DisplayName("an interrupted wait is treated as invalid and keeps the interrupt flag")
    void interruptedIsInvalid() {
        CountDownLatch release = new CountDownLatch(1);
        try (PatternChecker checker =
                new PatternChecker(Executors.newSingleThreadExecutor(), Duration.ofSeconds(5), pattern -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })) {
            Thread.currentThread().interrupt();
            assertThat(checker.check("abc")).isEqualTo(PatternChecker.Outcome.INVALID);
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            release.countDown();
        }
    }
}
