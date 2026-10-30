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

import cherry.mastersmith.targetdb.domain.TargetReadSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 参照先を読む全体の締め切り（Intent 261029-reference-lookup-cleanup の U1、BR8.4、NFR 設計 reliability-design.md 1.3・5.1。U2 との
 * 継ぎ目）。{@code withRead(REFERENCE, …)} の中で、最初の読み取りの前に1回だけ {@link #start} し、各文の前に {@link #narrow} で
 * 文の上限を絞った session を作る。空なら文を流さず {@link ReferenceReadFailure#ofBudgetExceeded()} の失敗にする。
 *
 * <p>時計は注入したもの（{@code team.md}）。{@code targetdb.config} を読まず、文1回の上限は渡された session から取る。
 *
 * @param clock 時計
 * @param deadline 締め切りの時点
 */
public record ReferenceReadDeadline(Clock clock, Instant deadline) {

    /** 文を流すのに要る残りの時間の下限。これより短ければ流さない。 */
    public static final Duration MIN_REMAINING = Duration.ofSeconds(1);

    /** 必須の値を確かめる。 */
    public ReferenceReadDeadline {
        Objects.requireNonNull(clock, "clock は必須です");
        Objects.requireNonNull(deadline, "deadline は必須です");
    }

    /**
     * 今の時点から全体の上限だけ先を締め切りにする。
     *
     * @param clock 時計
     * @param total 全体の上限（{@code TargetReadAccess.referenceReadTotal()}。点検で 1 秒以上と決まっている）
     * @return 締め切り
     */
    public static ReferenceReadDeadline start(Clock clock, Duration total) {
        Objects.requireNonNull(clock, "clock は必須です");
        Objects.requireNonNull(total, "total は必須です");
        return new ReferenceReadDeadline(clock, clock.instant().plus(total));
    }

    /**
     * 次の文の上限を残りの時間で絞った session を作る。残りを秒に切り上げ（1.2 秒は 2 秒）、session の上限と小さい方を上限にする。
     * 同じ DB の種類・接続・壊れた印（同じ {@code AtomicBoolean}）を持つため、絞った session で立てた印は元の session にも見える。
     *
     * @param session 借りた session（上限は REFERENCE の目的の上限）
     * @return 絞った session（残りが {@link #MIN_REMAINING} 未満なら空）
     */
    public Optional<TargetReadSession> narrow(TargetReadSession session) {
        Objects.requireNonNull(session, "session は必須です");
        Duration remaining = Duration.between(clock.instant(), deadline);
        if (remaining.compareTo(MIN_REMAINING) < 0) {
            return Optional.empty();
        }
        long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
        int limit = (int) Math.min(session.queryTimeoutSeconds(), seconds);
        return Optional.of(new TargetReadSession(session.product(), session.connection(), limit, session.brokenMark()));
    }
}
