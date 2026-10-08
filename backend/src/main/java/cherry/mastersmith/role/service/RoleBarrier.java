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
package cherry.mastersmith.role.service;

import cherry.mastersmith.role.domain.RoleOperation;

/**
 * ロールの操作の待ち合わせの口（{@code reliability-design.md} 2.2 の4つの点、{@code logical-components.md} の L8、BR8.6、計画の 4.4）。
 *
 * <p>本番は何もしない既定の部品（{@link NoOpRoleBarrier}）を使い、本番のコードの流れと順は変えない。同時の重なりの結合テストが
 * {@code @Primary} の部品で差し替えて、時間に頼らずに重なりを確実に作る。どの点も1つ目のトランザクションの中で呼ばれる。引数は操作の
 * 区分と ID・鍵だけで、追跡の TRACE に個人に関する値は出ない。
 *
 * <p>鍵（{@code key}）は、作成と名前の変更では名前の鍵（{@code RoleName.key()}）、削除と権限の保存・消す操作ではロールの ID の文字列、
 * 割り当てでは {@code <ロールの ID>:U<利用者 ID>}・{@code <ロールの ID>:G<グループの ID>}、作業ロールの切り替えでは利用者 ID の文字列
 * （B5。計画の 4.4）。作業ロールの切り替えはロールの行を排他しないため、{@link #beforeLock}・{@link #afterLock} を呼ばない。
 */
public interface RoleBarrier {

    /**
     * ロールの行の排他の直前に呼ばれる（排他を取る操作すべて）。
     *
     * @param operation 操作の区分
     * @param roleId ロールの ID
     */
    void beforeLock(RoleOperation operation, long roleId);

    /**
     * ロールの行の排他を取った直後に呼ばれる（同上）。
     *
     * @param operation 操作の区分
     * @param roleId ロールの ID
     */
    void afterLock(RoleOperation operation, long roleId);

    /**
     * 業務の判定（重なり・割り当ての有無）の後、書き込みの前に呼ばれる（作成・名前の変更・割り当て・作業ロールの切り替え）。
     *
     * @param operation 操作の区分
     * @param key 鍵
     */
    void afterCheck(RoleOperation operation, String key);

    /**
     * 書き込みと flush の後、確定の前に呼ばれる（割り当ての外しを除く変える操作。作業ロールの切り替えは保存を書いたときだけ）。
     *
     * @param operation 操作の区分
     * @param key 鍵
     */
    void afterWrite(RoleOperation operation, String key);
}
