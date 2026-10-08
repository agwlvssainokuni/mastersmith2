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

import cherry.mastersmith.group.service.GroupSummary;
import cherry.mastersmith.role.domain.RoleSource;
import cherry.mastersmith.user.service.UserAdminSummary;
import java.util.List;
import java.util.Objects;

/** ロールの割り当ての一覧の結果（FS の 2.8 の読み取り、契約 C7 の RoleAssignments、BR6.5・BR6.11・BR12.1）。 */
public sealed interface RoleAssignmentsResult permits RoleAssignmentsResult.Found, RoleAssignmentsResult.RoleNotFound {

    /**
     * 割り当ての一覧。
     *
     * @param users 利用者（利用者 ID の順。直接とグループ経由の和で、出どころを並べる）
     * @param groups ロールを割り当てたグループ（グループの ID の順）
     */
    record Found(List<AssignedUser> users, List<GroupSummary> groups) implements RoleAssignmentsResult {

        /** 値を写して持つ。 */
        public Found {
            users = List.copyOf(users);
            groups = List.copyOf(groups);
        }

        /** 件数だけを出す（氏名・メールアドレス・グループの名前を出さない）。 */
        @Override
        public String toString() {
            return "Found[users=" + users.size() + ", groups=" + groups.size() + "]";
        }
    }

    /** ロールがいない（404 {@code ROLE_NOT_FOUND}。監査なし）。 */
    record RoleNotFound() implements RoleAssignmentsResult {}

    /**
     * 割り当ての一覧の利用者の1行（BR12.1。氏名・メールアドレスは伏せ字の型のまま持ち、文字列にするのは応答の DTO の組み立ての時だけ）。
     *
     * @param user 利用者の要約（文字列にするとメールアドレスと氏名を伏せる型）
     * @param sources 出どころ（直接を先に、グループはグループの ID の順）
     */
    record AssignedUser(UserAdminSummary user, List<RoleSource> sources) {

        /** 値を写して持つ。 */
        public AssignedUser {
            Objects.requireNonNull(user, "user");
            sources = List.copyOf(sources);
        }

        /** 利用者 ID と出どころの数だけを出す。 */
        @Override
        public String toString() {
            return "AssignedUser[userId=" + user.userId() + ", sources=" + sources.size() + "]";
        }
    }
}
