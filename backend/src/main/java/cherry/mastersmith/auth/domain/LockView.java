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

import java.time.Instant;
import java.util.Objects;

/**
 * 1人の利用者のロックの判定の結果（Intent 260930-user-admin の U3、契約 C8 の lockViewsOf の値、BR1.7）。失敗回数そのものは持たない。
 *
 * <ul>
 *   <li>{@code locked}: 解除の予定の時刻があり、今の時刻 &lt; 解除の予定の時刻（ちょうどはロック中でない。{@link LockPolicy} と同じ境界）
 *   <li>{@code lockedUntil}: {@code locked} が真のときだけ解除の予定の時刻
 *   <li>{@code resettable}: 失敗回数が 1 以上、または解除の予定の時刻がある（R-05 の守り）。行が無ければ偽
 * </ul>
 *
 * @param locked ロック中か
 * @param lockedUntil 解除の予定の時刻（ロック中でなければ null）
 * @param resettable 失敗回数を戻せるか
 */
public record LockView(boolean locked, Instant lockedUntil, boolean resettable) {

    /** ロックの状態の行が無い利用者の判定（BR1.7）。 */
    public static final LockView NONE = new LockView(false, null, false);

    /** 値の組み合わせを確かめる（ロック中のときだけ解除の予定の時刻を持つ）。 */
    public LockView {
        if (locked != (lockedUntil != null)) {
            throw new IllegalArgumentException("解除の予定の時刻はロック中のときだけ持ちます");
        }
        if (locked && !resettable) {
            throw new IllegalArgumentException("ロック中の利用者は戻せる必要があります");
        }
    }

    /**
     * ロックの状態の行と今の時刻から判定する（DB を使わない純粋な関数）。
     *
     * @param state ロックの状態の行（無ければ null）
     * @param now 今の時刻
     * @return 判定の結果
     */
    public static LockView of(LoginAttemptState state, Instant now) {
        Objects.requireNonNull(now, "now");
        if (state == null) {
            return NONE;
        }
        Instant until = state.getLockedUntil();
        boolean locked = until != null && now.isBefore(until);
        boolean resettable = state.getConsecutiveFailures() >= 1 || until != null;
        return new LockView(locked, locked ? until : null, resettable);
    }
}
