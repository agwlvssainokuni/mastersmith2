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
 * ロックの状態の行を排他した直後に呼ぶ待ち合わせの口（Intent 260930-user-admin の U3、BR3.6、{@code reliability-design.md} 6節）。
 *
 * <p>失敗回数を戻す操作の1段目と、ログインの判定（実在の利用者の行を排他した直後。ダミーの行では呼ばない）の両方から呼ぶ。本番は
 * 何もしない既定の部品（{@link NoOpLoginAttemptBarrier}）を使い、本番のコードの流れと順は変えない。同時の重なりの結合テストが差し替えて、
 * 重なりを確実に作る（既存の {@code InvitationBarrier} と同じ考え方）。引数は利用者 ID だけで、追跡の TRACE に個人に関する値は出ない。
 */
public interface LoginAttemptBarrier {

    /**
     * ロックの状態の行の排他を得た直後に呼ばれる（行を排他したトランザクションの中）。
     *
     * @param userId 排他した行の利用者 ID
     */
    void afterLock(long userId);
}
