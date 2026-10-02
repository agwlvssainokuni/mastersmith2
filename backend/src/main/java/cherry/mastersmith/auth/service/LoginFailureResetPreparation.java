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
package cherry.mastersmith.auth.service;

/**
 * 失敗回数を戻す操作の1段目の結果（Intent 260930-user-admin の U3、契約 C8 の Authentication の口、FS の D6、BR4.5）。ロックの状態の
 * 行だけを排他して、戻せるかを判定する。失敗回数そのものは持たない。
 */
public sealed interface LoginFailureResetPreparation
        permits LoginFailureResetPreparation.Ready,
                LoginFailureResetPreparation.NothingToReset,
                LoginFailureResetPreparation.Busy {

    /** 戻せる（失敗回数が 1 以上、または解除の予定の時刻がある。R-05）。行は排他したまま。 */
    record Ready() implements LoginFailureResetPreparation {}

    /** 戻せない（行が無い、または失敗回数が 0 かつ解除の予定の時刻が無い）。行を作らない。 */
    record NothingToReset() implements LoginFailureResetPreparation {}

    /** 行の排他の待ちの上限切れ・行き詰まり。 */
    record Busy() implements LoginFailureResetPreparation {}
}
