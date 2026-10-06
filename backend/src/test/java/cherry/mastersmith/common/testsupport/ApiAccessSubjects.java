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
package cherry.mastersmith.common.testsupport;

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.web.AuthenticatedUserToken;
import cherry.mastersmith.common.security.ApiAccessLevel;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * 実行時の検査（{@code ApiAccessConsistencyIT}）の主体と、印ごとの期待の表（security-design 4.3 手順 6・7、要件 NFR1.3）。
 *
 * <p>主体はトークンの値だけで作り、内部DB に利用者を作らない（判定は主体の値だけで決まるため）。利用者の値は検査の中で作る仮の値
 * （固定の利用者 ID と {@code example.com} の見本のメールアドレス）で、秘密の値は持たない（NFR1.10）。
 *
 * <p>後の Intent が権限で守る API を足すときは、{@link Subject} に主体（権限を持つ・欠く利用者）を、{@link #EXPECTED} に印の値の
 * 行を足すだけで広げられる（security-design 7節）。
 */
public final class ApiAccessSubjects {

    /** 判定にかける主体。 */
    public enum Subject {

        /** 未ログイン（本番の入口と同じ匿名のトークン）。 */
        ANONYMOUS(new AnonymousAuthenticationToken(
                "api-access-check", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))),

        /** 管理者でないログイン中の利用者。 */
        NON_ADMIN_USER(new AuthenticatedUserToken(new AuthenticatedUser(1001L, "member@example.com", false))),

        /** 管理者のログイン中の利用者。 */
        ADMIN_USER(new AuthenticatedUserToken(new AuthenticatedUser(1002L, "admin@example.com", true)));

        private final Authentication authentication;

        Subject(Authentication authentication) {
            this.authentication = authentication;
        }

        /**
         * 判定に渡す認証の結果を返す。
         *
         * @return 認証の結果
         */
        public Authentication authentication() {
            return authentication;
        }
    }

    /** 印ごと・主体ごとに、通る（true）か止まる（false）かの期待。 */
    public static final Map<ApiAccessLevel, Map<Subject, Boolean>> EXPECTED = expectations();

    private ApiAccessSubjects() {}

    /**
     * 期待を返す。
     *
     * @param level 印の値
     * @param subject 主体
     * @return 通るなら true
     * @throws IllegalStateException 期待の表に行が無いとき（印の値を足したのに表を足し忘れたとき）
     */
    public static boolean expected(ApiAccessLevel level, Subject subject) {
        Map<Subject, Boolean> row = EXPECTED.get(level);
        if (row == null || !row.containsKey(subject)) {
            throw new IllegalStateException("期待の表に行がありません: " + level + " " + subject);
        }
        return row.get(subject);
    }

    private static Map<ApiAccessLevel, Map<Subject, Boolean>> expectations() {
        Map<ApiAccessLevel, Map<Subject, Boolean>> table = new EnumMap<>(ApiAccessLevel.class);
        table.put(ApiAccessLevel.PUBLIC, row(true, true, true));
        table.put(ApiAccessLevel.AUTHENTICATED, row(false, true, true));
        table.put(ApiAccessLevel.ADMIN, row(false, false, true));
        return Map.copyOf(table);
    }

    private static Map<Subject, Boolean> row(boolean anonymous, boolean nonAdminUser, boolean adminUser) {
        Map<Subject, Boolean> row = new EnumMap<>(Subject.class);
        row.put(Subject.ANONYMOUS, anonymous);
        row.put(Subject.NON_ADMIN_USER, nonAdminUser);
        row.put(Subject.ADMIN_USER, adminUser);
        return Map.copyOf(row);
    }
}
