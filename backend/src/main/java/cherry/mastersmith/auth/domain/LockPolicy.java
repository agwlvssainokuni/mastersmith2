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
package cherry.mastersmith.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** アカウントロックの判定（BR3.1〜BR3.6）。DB を使わない純粋な関数。 */
public final class LockPolicy {

    private LockPolicy() {}

    /**
     * ログインの試みの結果と、書き込む状態を決める。
     *
     * <ol>
     *   <li>解除時刻より前（{@code now < lockedUntil}）はロック中として拒否し、状態を変えない（BR3.3）
     *   <li>解除時刻ちょうど・以後はロックを解き、失敗回数を 0 に戻してから判定する（BR3.4）
     *   <li>一致すれば成功で、失敗回数を 0 にする（BR3.6）
     *   <li>一致しなければ失敗回数を1増やし、しきい値に達したら解除時刻＝現在＋ロックの時間でロックする（BR3.1、BR3.2）
     * </ol>
     *
     * @param state 排他を取った後の最新の状態
     * @param matched パスワードが一致したか
     * @param now 現在時刻
     * @param threshold ロックのしきい値（1 以上）
     * @param lockDuration ロックの時間
     * @return 判定の結果
     */
    public static LockDecision decide(
            LockState state, boolean matched, Instant now, int threshold, Duration lockDuration) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");
        if (threshold < 1) {
            throw new IllegalArgumentException("しきい値は 1 以上にしてください: " + threshold);
        }
        Instant lockedUntil = state.lockedUntil();
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            return new LockDecision(LoginOutcome.ACCOUNT_LOCKED, state, false);
        }
        if (matched) {
            return new LockDecision(LoginOutcome.SUCCEEDED, LockState.CLEAR, false);
        }
        int failures = (lockedUntil == null ? state.consecutiveFailures() : 0) + 1;
        if (failures >= threshold) {
            return new LockDecision(
                    LoginOutcome.PASSWORD_MISMATCH, new LockState(failures, now.plus(lockDuration)), true);
        }
        return new LockDecision(LoginOutcome.PASSWORD_MISMATCH, new LockState(failures, null), false);
    }
}
