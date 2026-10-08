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
package cherry.mastersmith.role.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 有効な作業ロールの決め方（{@code logical-components.md} の L6、BR7.1・BR7.2・BR7.4）。DB にも時計にも触れない純粋な関数である。
 *
 * <ul>
 *   <li>保存した作業ロールが利用者のロールに含まれればそれ（BR7.2。割り当てを外した後にまた割り当てられたら戻る。BR7.4）
 *   <li>含まれなければ「最初のロール」（ロールの ID の最も小さいもの。BR7.1）
 *   <li>利用者のロールが無ければ無し
 * </ul>
 *
 * <p>読み替えは保存を書き換えない（BR7.3）。この関数は値を返すだけで、保存の書き込みは切り替えの操作だけが行う。
 */
public final class WorkRoleChooser {

    private WorkRoleChooser() {}

    /**
     * 有効な作業ロールの ID を決める。
     *
     * @param roleIds 利用者のロールの ID（直接 ∪ グループ経由。重なりは1つとして扱う）
     * @param stored 保存した作業ロールの ID（無ければ空）
     * @return 有効な作業ロールの ID（ロールが無ければ空）
     */
    public static OptionalLong choose(Collection<Long> roleIds, OptionalLong stored) {
        Objects.requireNonNull(roleIds, "roleIds");
        Objects.requireNonNull(stored, "stored");
        if (stored.isPresent() && roleIds.contains(stored.getAsLong())) {
            return stored;
        }
        return roleIds.stream().mapToLong(Long::longValue).min();
    }
}
