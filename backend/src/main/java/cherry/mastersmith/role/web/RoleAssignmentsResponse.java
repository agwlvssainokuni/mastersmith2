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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.service.RoleAssignmentsResult;
import java.util.List;

/**
 * ロールの割り当ての一覧の応答（契約 C7 の RoleAssignments、BR6.5・BR6.11・BR12.1・BR12.2）。
 *
 * @param users 利用者（利用者 ID の順、出どころつき）
 * @param groups ロールを割り当てたグループ（グループの ID の順）
 */
public record RoleAssignmentsResponse(List<User> users, List<Group> groups) {

    /**
     * 利用者1人分（氏名とメールアドレスの値を取り出すのはここだけ。パスワードのハッシュ値・ロックの内部の値を型に持たない）。
     *
     * @param userId 利用者 ID
     * @param displayName 氏名
     * @param email メールアドレス
     * @param suspended 利用停止中か
     * @param sources 出どころ
     */
    public record User(
            long userId, String displayName, String email, boolean suspended, List<RoleSourceResponse> sources) {

        /** 氏名とメールアドレスを伏せて文字列にする。 */
        @Override
        public String toString() {
            return "User[userId=" + userId + ", displayName=***, email=***, suspended=" + suspended + ", sources="
                    + sources.size() + "]";
        }
    }

    /**
     * グループ1つ分。
     *
     * @param groupId グループの ID
     * @param name 名前
     */
    public record Group(long groupId, String name) {}

    static RoleAssignmentsResponse from(RoleAssignmentsResult.Found found) {
        List<User> users = found.users().stream()
                .map(assigned -> new User(
                        assigned.user().userId(),
                        assigned.user().displayName(),
                        assigned.user().email(),
                        assigned.user().suspended(),
                        assigned.sources().stream()
                                .map(RoleSourceResponse::from)
                                .toList()))
                .toList();
        List<Group> groups = found.groups().stream()
                .map(group -> new Group(group.groupId(), group.name()))
                .toList();
        return new RoleAssignmentsResponse(users, groups);
    }

    /** 件数だけを出す。 */
    @Override
    public String toString() {
        return "RoleAssignmentsResponse[users=" + users.size() + ", groups=" + groups.size() + "]";
    }
}
