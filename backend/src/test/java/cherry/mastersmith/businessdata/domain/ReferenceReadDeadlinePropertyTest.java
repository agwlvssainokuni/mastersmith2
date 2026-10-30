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
import static org.mockito.Mockito.mock;

import cherry.mastersmith.businessdata.testsupport.SteppingClock;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import java.sql.Connection;
import java.time.Duration;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * 参照先を読む全体の締め切りの性質ベースのテスト（jqwik。BR8.4）。失敗のときの乱数の種は失敗の報告に残り、
 * {@code @Property(seed = "...")} に与えて再現する。
 */
class ReferenceReadDeadlinePropertyTest {

    private static final Connection CONNECTION = mock(Connection.class);

    @Property
    @Label("a narrowed limit is at least 1, never above the session limit and covers the remaining time")
    void narrowedLimitStaysInRange(
            @ForAll @IntRange(min = 1, max = 60) int totalSeconds,
            @ForAll @LongRange(min = 0, max = 70_000) long elapsedMillis,
            @ForAll @IntRange(min = 1, max = 30) int sessionSeconds) {
        SteppingClock clock = new SteppingClock(1_800_000_000_000L, 0);
        ReferenceReadDeadline deadline = ReferenceReadDeadline.start(clock, Duration.ofSeconds(totalSeconds));
        clock.advance(elapsedMillis);
        long remainingMillis = totalSeconds * 1000L - elapsedMillis;
        TargetReadSession session = new TargetReadSession(DatabaseProduct.MYSQL, CONNECTION, sessionSeconds);

        Optional<TargetReadSession> narrowed = deadline.narrow(session);

        if (remainingMillis < 1000) {
            assertThat(narrowed).isEmpty();
        } else {
            int limit = narrowed.orElseThrow().queryTimeoutSeconds();
            assertThat(limit).isBetween(1, sessionSeconds);
            long roundedUp = (remainingMillis + 999) / 1000;
            assertThat(limit).isEqualTo((int) Math.min(sessionSeconds, roundedUp));
            assertThat(narrowed.get().brokenMark()).isSameAs(session.brokenMark());
        }
    }
}
