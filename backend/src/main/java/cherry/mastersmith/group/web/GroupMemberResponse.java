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
package cherry.mastersmith.group.web;

import cherry.mastersmith.group.domain.GroupMember;

/**
 * 詳細のメンバー1人分の応答（契約 C6 の Member、BR7.4・BR9.1・BR9.2）。氏名とメールアドレスの値を取り出すのはここだけで、文字列にすると
 * 伏せる。パスワードのハッシュ値・ロックの判定の内部の値を型に持たない。
 *
 * @param userId 利用者 ID
 * @param displayName 氏名
 * @param email メールアドレス
 * @param suspended 利用停止中か
 */
public record GroupMemberResponse(long userId, String displayName, String email, boolean suspended) {

    /**
     * 業務処理のメンバーから作る。
     *
     * @param member メンバー
     * @return 応答
     */
    static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(member.userId(), member.displayName(), member.email(), member.suspended());
    }

    /** 氏名とメールアドレスを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "GroupMemberResponse[userId=" + userId + ", displayName=***, email=***, suspended=" + suspended + "]";
    }
}
