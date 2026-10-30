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
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 参照先を読む全体の締め切りの単体テスト（T-T2、BR8.4、reliability-design.md 1.3・5.1）。時計は注入し、実時刻に頼らない。 */
class ReferenceReadDeadlineTest {

    private static final long START = 1_800_000_000_000L;

    private final Connection connection = mock(Connection.class);

    private final TargetReadSession session = new TargetReadSession(DatabaseProduct.POSTGRESQL, connection, 5);

    /** 全体の上限 10 秒の締め切りを作り、残りが {@code remainingMillis} になるまで時計を進める。 */
    private Optional<TargetReadSession> narrowWithRemaining(long remainingMillis) {
        SteppingClock clock = new SteppingClock(START, 0);
        ReferenceReadDeadline deadline = ReferenceReadDeadline.start(clock, Duration.ofSeconds(10));
        clock.advance(10_000 - remainingMillis);
        return deadline.narrow(session);
    }

    @Test
    @DisplayName("the deadline is the start of the read plus the total limit")
    void startAddsTheTotal() {
        SteppingClock clock = new SteppingClock(START, 0);

        ReferenceReadDeadline deadline = ReferenceReadDeadline.start(clock, Duration.ofSeconds(10));

        assertThat(deadline.deadline()).isEqualTo(Instant.ofEpochMilli(START + 10_000));
        assertThat(ReferenceReadDeadline.MIN_REMAINING).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("with 0.5 seconds left nothing is run")
    void halfASecondLeft() {
        assertThat(narrowWithRemaining(500)).isEmpty();
        assertThat(narrowWithRemaining(999)).isEmpty();
        assertThat(narrowWithRemaining(0)).isEmpty();
        assertThat(narrowWithRemaining(-2_000)).as("締め切りを過ぎた").isEmpty();
    }

    @Test
    @DisplayName("with exactly 1.0 second left the statement limit is 1 second")
    void oneSecondLeft() {
        assertThat(narrowWithRemaining(1_000))
                .hasValueSatisfying(s -> assertThat(s.queryTimeoutSeconds()).isEqualTo(1));
    }

    @Test
    @DisplayName("with 2.3 seconds left the limit is rounded up to 3 and stays under the session limit")
    void twoPointThreeSecondsLeft() {
        assertThat(narrowWithRemaining(2_300))
                .hasValueSatisfying(s -> assertThat(s.queryTimeoutSeconds()).isEqualTo(3));
        assertThat(narrowWithRemaining(10_000))
                .as("残り 10 秒でも session の上限 5 秒を超えない")
                .hasValueSatisfying(s -> assertThat(s.queryTimeoutSeconds()).isEqualTo(5));
        assertThat(narrowWithRemaining(4_001))
                .hasValueSatisfying(s -> assertThat(s.queryTimeoutSeconds()).isEqualTo(5));
    }

    @Test
    @DisplayName("the narrowed session shares the product, the connection and the broken mark")
    void sharesConnectionAndBrokenMark() {
        TargetReadSession narrowed = narrowWithRemaining(2_300).orElseThrow();

        assertThat(narrowed.product()).isEqualTo(DatabaseProduct.POSTGRESQL);
        assertThat(narrowed.connection()).isSameAs(connection);
        assertThat(narrowed.brokenMark()).isSameAs(session.brokenMark());
        narrowed.markBroken();
        assertThat(session.isBroken()).as("絞った session で立てた印が元の session に見える").isTrue();
    }

    @Test
    @DisplayName("each narrow reads the clock again so later statements get less time")
    void laterStatementsGetLess() {
        SteppingClock clock = new SteppingClock(START, 0);
        ReferenceReadDeadline deadline = ReferenceReadDeadline.start(clock, Duration.ofSeconds(10));

        assertThat(deadline.narrow(session).orElseThrow().queryTimeoutSeconds()).isEqualTo(5);
        clock.advance(7_500);
        assertThat(deadline.narrow(session).orElseThrow().queryTimeoutSeconds()).isEqualTo(3);
        clock.advance(2_000);
        assertThat(deadline.narrow(session)).isEmpty();
    }
}
