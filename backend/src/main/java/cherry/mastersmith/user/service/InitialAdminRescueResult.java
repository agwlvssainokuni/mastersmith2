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

import cherry.mastersmith.user.domain.InitialAdminRescueCondition;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 初期管理者の救済の結果（Intent 261004-safety-carryover の FR1.1・FR1.3）。想定内の場合を例外にせず、この型で返す。
 *
 * <p>メールアドレスを持たない（メソッドの呼び出しの追跡が戻り値を TRACE に文字列で出すため）。
 */
public sealed interface InitialAdminRescueResult {

    /** 設定のメールアドレスの利用者がいない（呼び出し元が作成する）。 */
    record NotFound() implements InitialAdminRescueResult {}

    /** 利用者はいて、救済の条件のどれにも当たらない（何も書かない）。 */
    record NotNeeded() implements InitialAdminRescueResult {}

    /**
     * 救済した。
     *
     * @param userId 救済した利用者 ID
     * @param conditions 当たった条件の集まり（空でない。宣言の順の変えられない写しで持つ）
     */
    record Rescued(long userId, Set<InitialAdminRescueCondition> conditions) implements InitialAdminRescueResult {

        /** 条件が空でないことを確かめ、変えられない写しにする。 */
        public Rescued {
            Objects.requireNonNull(conditions, "conditions");
            if (conditions.isEmpty()) {
                throw new IllegalArgumentException("救済の条件が空です");
            }
            conditions = Collections.unmodifiableSet(EnumSet.copyOf(conditions));
        }
    }
}
