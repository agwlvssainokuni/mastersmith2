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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * group が U4 role に出す読み取りと排他の口（契約 C4 に足した形、BR3.6・BR5.3・BR6.4・BR6.5、{@code logical-components.md} の L4）。
 *
 * <p>引数と戻り値は ID・名前・真偽値だけで、個人に関する値を持たない。所属は要求ごとに内部DB から読み、写しを持たない（BR3.6）。
 */
public interface GroupMembershipQuery {

    /**
     * 利用者が属するグループの ID を返す（1回の読み取り。BR3.6・BR6.4）。
     *
     * @param userId 利用者 ID
     * @return グループの ID の集合（属していなければ空）
     */
    Set<Long> groupIdsOfUser(long userId);

    /**
     * グループがいるかを返す（BR6.4）。
     *
     * @param groupId グループの ID
     * @return いれば true
     */
    boolean exists(long groupId);

    /**
     * グループの要約を ID の順に返す（いない ID は含めない。BR6.4）。
     *
     * @param groupIds グループの ID の集合
     * @return 要約の一覧
     */
    List<GroupSummary> summaries(Set<Long> groupIds);

    /**
     * グループの行を排他する（BR5.3）。呼び出し元のトランザクションの中でだけ呼べる（無ければ {@link IllegalStateException}）。
     * {@link GroupRowLock.Busy} なら呼び出し元は巻き戻して断る。
     *
     * @param groupId グループの ID
     * @return 排他の結果
     */
    GroupRowLock lockForAssignment(long groupId);

    /**
     * グループのメンバーの利用者 ID を、グループの ID の集合でまとめて1回で読む（BR6.5）。存在しないグループは結果に含めず、メンバーが
     * 0 のグループは空の集合で含める。
     *
     * @param groupIds グループの ID の集合
     * @return グループの ID ごとのメンバーの利用者 ID の集合
     */
    Map<Long, Set<Long>> memberUserIds(Set<Long> groupIds);
}
