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
package cherry.mastersmith.businessdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.SteppingClock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 締め切りつきの正規表現の照合の単体テスト（Intent 261016-business-data-views の U2、BR4.1・BR4.2、NFR4.7、F4: A）。締め切りは
 * 注入した時計で決め、実時刻と {@code sleep} に頼らない。
 */
class DeadlinePatternMatcherTest {

    private static final String HASH = "0123456789abcdef".repeat(4);

    private static final String HEAVY_INPUT = "a".repeat(30) + "!";

    /** 決めた順に値を返す時計（最後の値を繰り返す）。読んだ回数を数える。 */
    private static final class ScriptedClock extends Clock {

        private final Deque<Long> values;

        private long last;

        private int reads;

        ScriptedClock(Long... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public long millis() {
            reads++;
            if (!values.isEmpty()) {
                last = values.poll();
            }
            return last;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("light patterns are matched against the whole value")
    void wholeValue() {
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(new ScriptedClock(0L));
        DeadlinePatternMatcher.Budget budget = matcher.budget();

        assertThat(matcher.match(HASH, "[A-Z]{3}", "ABC", budget)).isEqualTo(DeadlinePatternMatcher.MatchResult.MATCH);
        assertThat(matcher.match(HASH, "[A-Z]{3}", "xABCx", budget))
                .as("部分一致では通さない")
                .isEqualTo(DeadlinePatternMatcher.MatchResult.MISMATCH);
        assertThat(matcher.match(HASH, "(a+)+$", HEAVY_INPUT, budget))
                .as("JDK 25 では重くない式")
                .isEqualTo(DeadlinePatternMatcher.MatchResult.MISMATCH);
        assertThat(matcher.match(HASH, "[", "x", budget))
                .as("組み立てられない式は合わないものとして扱う")
                .isEqualTo(DeadlinePatternMatcher.MatchResult.MISMATCH);
    }

    @Test
    @DisplayName("a value is stopped exactly when the clock reaches its deadline of 100 ms, not one ms before")
    void perValueDeadlineBoundary() {
        // 始め 0、1回目の見張りは 99（締め切りの前）、2回目は 100（締め切りちょうど）。
        ScriptedClock clock = new ScriptedClock(0L, 99L, 100L, 100L);
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(clock);
        DeadlinePatternMatcher.Budget budget = matcher.budget();

        assertThat(matcher.match(HASH, "((a+)+)+c", HEAVY_INPUT, budget))
                .isEqualTo(DeadlinePatternMatcher.MatchResult.TIMEOUT);
        assertThat(clock.reads).as("始め・見張り2回・終わり").isEqualTo(4);
        assertThat(budget.remainingMillis()).isEqualTo(200);
    }

    @Test
    @DisplayName("the total of 300 ms per request shortens the deadline of later values and then skips matching")
    void totalBudget() {
        // 1つ目の値で 250 ms を使い、2つ目は残りの 50 ms で打ち切り、3つ目は照合しない。
        ScriptedClock clock = new ScriptedClock(0L, 250L, 250L, 299L, 300L, 300L);
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(clock);
        DeadlinePatternMatcher.Budget budget = matcher.budget();

        assertThat(matcher.match(HASH, "[a-z]+", "abc", budget)).isEqualTo(DeadlinePatternMatcher.MatchResult.MATCH);
        assertThat(budget.remainingMillis()).isEqualTo(50);
        assertThat(matcher.match(HASH, "(.*a){12}", HEAVY_INPUT, budget))
                .isEqualTo(DeadlinePatternMatcher.MatchResult.TIMEOUT);
        assertThat(budget.remainingMillis()).isEqualTo(0);
        int reads = clock.reads;
        assertThat(matcher.match(HASH, "[a-z]+", "abc", budget))
                .as("合計を使い切ったら照合しない")
                .isEqualTo(DeadlinePatternMatcher.MatchResult.TIMEOUT);
        assertThat(clock.reads).isEqualTo(reads);
    }

    @Test
    @DisplayName("both heavy patterns are stopped by a moving clock")
    void heavyPatternsStop() {
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(new SteppingClock(0, 1));

        assertThat(matcher.match(HASH, "((a+)+)+c", HEAVY_INPUT, matcher.budget()))
                .isEqualTo(DeadlinePatternMatcher.MatchResult.TIMEOUT);
        assertThat(matcher.match(HASH, "(.*a){12}", HEAVY_INPUT, matcher.budget()))
                .isEqualTo(DeadlinePatternMatcher.MatchResult.TIMEOUT);
    }

    @Test
    @DisplayName("compiled patterns are kept per DSL and pattern and the cache is bounded")
    void cache() {
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(new ScriptedClock(0L));
        DeadlinePatternMatcher.Budget budget = matcher.budget();

        matcher.match(HASH, "a", "a", budget);
        matcher.match(HASH, "a", "a", budget);
        matcher.match("f".repeat(64), "a", "a", budget);
        assertThat(matcher.cachedPatterns()).isEqualTo(2);
        for (int i = 0; i < DeadlinePatternMatcher.CACHE_LIMIT + 10; i++) {
            matcher.match(HASH, "a{" + i + "}", "", budget);
        }
        assertThat(matcher.cachedPatterns()).isEqualTo(DeadlinePatternMatcher.CACHE_LIMIT);
    }
}
