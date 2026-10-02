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
package cherry.mastersmith.user.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 管理者の行と対象の行の排他の結果（Intent 260930-user-admin の U3、契約 C8 の lockAdminRowsInIdOrder、BR3.1・BR3.5）。
 *
 * <p>排他を取れた（{@link Locked}）か、待ちの上限切れ・行き詰まりで取れなかった（{@link Busy}）か。値は投影だけで持ち、JPA の
 * エンティティを持たない（U1 のレビュー R-03 の守り）。
 */
public sealed interface AdminRowsLock permits AdminRowsLock.Locked, AdminRowsLock.Busy {

    /**
     * 排他を取れた。対象の要約と有効な管理者の集合は、排他の後に別の問い合わせで読んだ値（待つ間に確定した変更を含む）。
     *
     * @param target 対象の要約（いなければ空）
     * @param activeAdminIds 排他の後に数えた有効な管理者（印あり・停止中でない。ロック中も含む）の利用者 ID
     */
    record Locked(Optional<UserAdminSummary> target, Set<Long> activeAdminIds) implements AdminRowsLock {

        /** 値が null でないことを確かめ、集合を変えられない写しにする。 */
        public Locked {
            Objects.requireNonNull(target, "target");
            activeAdminIds = Set.copyOf(Objects.requireNonNull(activeAdminIds, "activeAdminIds"));
        }
    }

    /** 排他を取れなかった（待ちの上限切れ・行き詰まり）。 */
    record Busy() implements AdminRowsLock {}
}
