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
package cherry.mastersmith.group.service;

import cherry.mastersmith.group.domain.GroupOperation;

/**
 * グループの操作の待ち合わせの口（{@code reliability-design.md} 2.1 の4つの点、{@code logical-components.md} の L6）。
 *
 * <p>本番は何もしない既定の部品（{@link NoOpGroupBarrier}）を使い、本番のコードの流れと順は変えない。同時の重なりの結合テストが
 * {@code @Primary} の部品で差し替えて、時間に頼らずに重なりを確実に作る。どの点も1つ目のトランザクションの中で呼ばれる。引数は操作の
 * 区分と ID・鍵だけで、追跡の TRACE に個人に関する値は出ない。
 *
 * <p>鍵（{@code key}）は、作成と名前の変更では名前の鍵（{@code GroupName.key()}）、メンバーの追加と外しでは
 * {@code <グループの ID>:<利用者 ID>}、削除ではグループの ID の文字列。
 */
public interface GroupBarrier {

    /**
     * グループの行の排他の直前に呼ばれる（名前の変更・削除・メンバーの追加と外し）。
     *
     * @param operation 操作の区分
     * @param groupId グループの ID
     */
    void beforeLock(GroupOperation operation, long groupId);

    /**
     * グループの行の排他を取った直後に呼ばれる（同上）。
     *
     * @param operation 操作の区分
     * @param groupId グループの ID
     */
    void afterLock(GroupOperation operation, long groupId);

    /**
     * 業務の判定（重なり・有無・メンバーか）の後、書き込みの前に呼ばれる（作成・名前の変更・メンバーの追加）。
     *
     * @param operation 操作の区分
     * @param key 鍵
     */
    void afterCheck(GroupOperation operation, String key);

    /**
     * 書き込みと flush の後、確定の前に呼ばれる（すべての変える操作）。
     *
     * @param operation 操作の区分
     * @param key 鍵
     */
    void afterWrite(GroupOperation operation, String key);
}
