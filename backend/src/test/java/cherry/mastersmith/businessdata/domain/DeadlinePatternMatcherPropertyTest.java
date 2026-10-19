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
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

/**
 * 締め切りつきの正規表現の照合の性質ベースのテスト（jqwik。Intent 261016-business-data-views の U2、NFR7.6）。時計は読むたびに進む
 * 注入した時計で、実時刻に頼らない。
 */
class DeadlinePatternMatcherPropertyTest {

    private static final String HASH = "0123456789abcdef".repeat(4);

    private static final List<String> PATTERNS =
            List.of("((a+)+)+c", "(.*a){12}", "[a-z]+", "(a|aa)+b", "a*", "(a+)+$");

    @Property(tries = 50)
    @Label("matching never runs past the total deadline of a request")
    void totalDeadlineIsKept(
            @ForAll @Size(min = 1, max = 8) List<@StringLength(max = 40) @Chars({'a', 'b', '!'}) String> values,
            @ForAll @IntRange(min = 0, max = 5) int patternIndex,
            @ForAll @IntRange(min = 1, max = 3) int step) {
        SteppingClock clock = new SteppingClock(0, step);
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(clock);
        DeadlinePatternMatcher.Budget budget = matcher.budget();
        boolean exhausted = false;
        for (String value : values) {
            long before = clock.peek();
            DeadlinePatternMatcher.MatchResult result =
                    matcher.match(HASH, PATTERNS.get(patternIndex), value + "!", budget);
            long used = clock.peek() - before;
            if (exhausted) {
                assertThat(result).as("合計を使い切った後は照合しない").isEqualTo(DeadlinePatternMatcher.MatchResult.TIMEOUT);
                assertThat(used).isZero();
            }
            assertThat(used)
                    .as("値ごとの締め切りを、見張りの1回分より長くは超えない")
                    .isLessThanOrEqualTo(DeadlinePatternMatcher.PER_VALUE_MILLIS + 3L * step);
            exhausted = budget.remainingMillis() <= 0;
        }
        assertThat(clock.peek())
                .as("全体で合計の締め切りを、値ごとの見張りの分より長くは超えない")
                .isLessThanOrEqualTo(DeadlinePatternMatcher.TOTAL_MILLIS + 3L * step * values.size());
    }
}
