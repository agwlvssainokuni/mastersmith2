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
package cherry.mastersmith.user.domain;

import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 初期管理者の救済の条件（Intent 261004-safety-carryover の FR1.1・FR1.6a）。
 *
 * <p>起動のたびに、初期管理者の設定のメールアドレスの利用者がこの条件のどれかに当たれば救う。宣言の順が、監査の行に記録するときの並びの順
 * である（{@link #code(Set)}）。
 */
public enum InitialAdminRescueCondition {
    /** 利用を止められている。 */
    SUSPENDED,
    /** 管理者の印が無い。 */
    NO_ADMIN,
    /** パスワードが設定の値と一致しない。 */
    PASSWORD;

    /** 条件をつなぐ区切り。 */
    public static final String SEPARATOR = "+";

    /**
     * 当たった条件の集まりを、監査の行とログに載せる短い値にする（FR1.6a）。
     *
     * <p>渡された順に依らず、宣言の順（{@code SUSPENDED}・{@code NO_ADMIN}・{@code PASSWORD}）に {@code +} でつなぐ。3つすべてで
     * {@code SUSPENDED+NO_ADMIN+PASSWORD}（27 文字）になり、監査の列（32 文字）に収まる。DB にも時計にも触れない純粋な関数である。
     *
     * @param conditions 当たった条件の集まり（空でないこと）
     * @return つないだ値
     * @throws NullPointerException 集まりまたはその要素が null のとき
     * @throws IllegalArgumentException 集まりが空のとき（救済の条件の無い値は作らない）
     */
    public static String code(Set<InitialAdminRescueCondition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("救済の条件が空です");
        }
        // contains(null) は Set.of の集まりで NullPointerException を投げるため、要素を順に見る。
        if (conditions.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("conditions に null が含まれます");
        }
        StringJoiner joiner = new StringJoiner(SEPARATOR);
        for (InitialAdminRescueCondition condition : values()) {
            if (conditions.contains(condition)) {
                joiner.add(condition.name());
            }
        }
        return joiner.toString();
    }
}
