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

/**
 * ロックの判定の入力と出力の状態。
 *
 * @param consecutiveFailures 連続失敗回数
 * @param lockedUntil ロックの解除時刻（ロックしていなければ null）
 */
public record LockState(int consecutiveFailures, Instant lockedUntil) {

    /** 失敗回数 0・ロックなしの状態。 */
    public static final LockState CLEAR = new LockState(0, null);

    /** 値の決まりを確かめる。 */
    public LockState {
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("連続失敗回数は 0 以上にしてください: " + consecutiveFailures);
        }
    }
}
