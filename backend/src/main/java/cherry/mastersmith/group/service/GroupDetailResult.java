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

import cherry.mastersmith.group.domain.GroupMember;
import java.util.List;
import java.util.Objects;

/** グループの詳細の結果（FS の 2.7、BR7.4・BR9.1）。 */
public sealed interface GroupDetailResult permits GroupDetailResult.Found, GroupDetailResult.NotFound {

    /**
     * 見つかった。メンバーは足した順に全件（ページ送りしない）。メンバーの文字列は氏名とメールアドレスを伏せる。
     *
     * @param groupId グループの ID
     * @param name 名前
     * @param members メンバー
     */
    record Found(long groupId, String name, List<GroupMember> members) implements GroupDetailResult {

        /** 値を写して持つ。 */
        public Found {
            Objects.requireNonNull(name, "name");
            members = List.copyOf(members);
        }
    }

    /** グループがいない（404 {@code GROUP_NOT_FOUND}。読み取りのため監査なし）。 */
    record NotFound() implements GroupDetailResult {}
}
