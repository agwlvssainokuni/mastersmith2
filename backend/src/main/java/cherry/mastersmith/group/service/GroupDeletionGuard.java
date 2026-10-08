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

import java.util.Map;
import java.util.Set;

/**
 * グループへのロールの割り当てを問う口（ADR-002、契約 C4、BR6.1・BR6.2、{@code logical-components.md} の L5）。
 *
 * <p>{@code group} が定義し、{@code role} が実装する。{@code group} は {@code role} のクラス・表を知らず、既定の実装を持たない（実装が
 * 無ければアプリは起動しない）。B3 では {@code role} のパッケージの仮の実装が数 0・削除してよいを返し、B5 で本物に置き換える（BR6.3）。
 */
public interface GroupDeletionGuard {

    /**
     * グループを消してよいかを答える（割り当ての数が 0 なら {@link DeletionDecision.Allowed}、1 以上なら
     * {@link DeletionDecision.Blocked}。{@link #assignedRoleCounts(Set)} と同じ数で答える）。
     *
     * @param groupId グループの ID
     * @return 答え
     */
    DeletionDecision canDelete(long groupId);

    /**
     * グループごとのロールの割り当ての数をまとめて1回で返す（一覧の1ページ。BR6.2）。渡した ID のすべてにキーを返す（無ければ 0）。
     *
     * @param groupIds グループの ID の集合
     * @return グループの ID ごとの割り当ての数
     */
    Map<Long, Integer> assignedRoleCounts(Set<Long> groupIds);
}
