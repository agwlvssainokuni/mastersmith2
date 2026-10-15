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
package cherry.mastersmith.invitation.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/** 招待中の人の認証のテストで、招待の ID を利用者の ID と重ならない値に書き換える手伝い。 */
public final class InvitedPersonIds {

    /** 書き換える ID の下限。 */
    public static final long BASE = 910_000;

    private InvitedPersonIds() {}

    /**
     * 指定したメールアドレスの招待の ID を書き換え、書き換えた ID を返す。ID は、下限・内部DB の招待の ID の最大＋1・利用者の ID の最大＋1 の
     * うち最も大きい値にする。メールアドレスのハッシュ値で選ぶと、同じ内部DB の別の招待の ID と重なり主キーの違反で落ちうるため。
     *
     * @param jdbc 内部DB への接続
     * @param email 招待のメールアドレス
     * @return 書き換えた招待の ID
     */
    public static long reassign(JdbcTemplate jdbc, String email) {
        Long id = jdbc.queryForObject(
                "SELECT GREATEST(?, COALESCE((SELECT MAX(invitation_id) FROM invitations), 0) + 1,"
                        + " COALESCE((SELECT MAX(user_id) FROM users), 0) + 1)",
                Long.class,
                BASE);
        if (id == null) {
            throw new IllegalStateException("招待の ID を選べない");
        }
        jdbc.update("UPDATE invitations SET invitation_id = ? WHERE email = ?", id, email);
        return id;
    }
}
