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
package cherry.mastersmith.useradmin.domain;

/**
 * 拒否の判定の純粋な関数（{@link RejectionPolicy#decide}）に渡す事実の組（BR2.1・BR2.2）。業務処理が排他の後に読んだ値から作る。
 * 値は真偽だけで、個人に関する値を持たない。
 *
 * @param targetExists 対象の利用者がいるか
 * @param targetIsOperator 対象が操作した人自身か
 * @param targetSuspended 対象が停止中か
 * @param targetAdmin 対象が管理者の印を持つか
 * @param targetResettable 対象の失敗回数を戻せるか（失敗回数を戻す操作だけで使う。LockView の resettable と同じ定義）
 * @param leavesNoActiveAdmin 排他の後の有効な管理者から対象を除くと 0 人になるか（印を外す・止めるだけで使う。BR3.2）
 */
public record OperationFacts(
        boolean targetExists,
        boolean targetIsOperator,
        boolean targetSuspended,
        boolean targetAdmin,
        boolean targetResettable,
        boolean leavesNoActiveAdmin) {

    /**
     * 対象がいないときの事実の組を作る（対象についての事実はどれも偽）。
     *
     * @return 事実の組
     */
    public static OperationFacts targetMissing() {
        return new OperationFacts(false, false, false, false, false, false);
    }
}
