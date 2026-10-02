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

/**
 * 対象の利用者の行だけの排他の結果（Intent 260930-user-admin の U3、契約 C8 の lockUserRow、BR3.3・BR3.5。停止を解く操作だけが
 * 使う）。値は投影だけで持ち、JPA のエンティティを持たない。
 */
public sealed interface UserRowLock permits UserRowLock.Locked, UserRowLock.Busy {

    /**
     * 排他を取れた。
     *
     * @param target 対象の要約（排他の後に読んだ値。いなければ空）
     */
    record Locked(Optional<UserAdminSummary> target) implements UserRowLock {

        /** 値が null でないことを確かめる。 */
        public Locked {
            Objects.requireNonNull(target, "target");
        }
    }

    /** 排他を取れなかった（待ちの上限切れ・行き詰まり）。 */
    record Busy() implements UserRowLock {}
}
