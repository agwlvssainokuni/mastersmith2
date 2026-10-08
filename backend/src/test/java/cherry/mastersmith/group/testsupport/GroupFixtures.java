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
package cherry.mastersmith.group.testsupport;

import cherry.mastersmith.group.domain.GroupName;
import cherry.mastersmith.group.domain.GroupNameValidation;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * グループの結合テストの前提を作る手伝い（Intent 261004-role-menu の U3）。利用者・グループ・メンバーを本番のコードの書き込みを
 * 通さずに作る（テストのためだけの API・操作を本番のコードに足さないため）。
 *
 * <p>利用者のメールアドレスは予約のドメイン（{@code example.com}）だけ、氏名は明らかな見本の値にする。グループの名前はテストごとに
 * 重ならないよう印を付ける。名前の鍵は本番と同じく {@link GroupName} から作る。
 */
public final class GroupFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final UserRepository users;

    private final JdbcTemplate jdbc;

    /**
     * 作る。
     *
     * @param users 利用者の表
     * @param jdbc 内部DB への問い合わせ
     */
    public GroupFixtures(UserRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    /**
     * 重ならないグループの名前を作る。
     *
     * @param prefix 名前の頭
     * @return 名前
     */
    public static String uniqueName(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 登録の終わった利用者を作る（管理者の印なし・停止なし）。
     *
     * @param displayName 氏名（見本の値）
     * @return 利用者 ID
     */
    public long user(String displayName) {
        User user = users.save(new User(
                "group-member-" + UUID.randomUUID() + "@example.com",
                "$2a$04$groupfixturehash",
                false,
                Instant.parse("2026-10-01T00:00:00Z"),
                new Preferences(displayName, Language.JA, Theme.SYSTEM, FontSize.MD)));
        return user.getUserId();
    }

    /**
     * グループを作る（作成と更新の日時は同じ値）。
     *
     * @param name 名前（{@link GroupName} の規則に合う値）
     * @param createdAt 作成の日時
     * @return グループの ID
     */
    public long group(String name, Instant createdAt) {
        GroupName groupName = ((GroupNameValidation.Valid) GroupName.parse(name)).name();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    var statement = connection.prepareStatement(
                            "INSERT INTO groups (name, name_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                            new String[] {"group_id"});
                    statement.setString(1, groupName.value());
                    statement.setString(2, groupName.key());
                    statement.setObject(3, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
                    statement.setObject(4, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
                    return statement;
                },
                keys);
        return keys.getKeyAs(Long.class);
    }

    /**
     * グループを作る（作成の日時は決まった値）。
     *
     * @param name 名前
     * @return グループの ID
     */
    public long group(String name) {
        return group(name, Instant.parse("2026-10-02T00:00:00Z"));
    }

    /**
     * メンバーの行を足す。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @param addedAt 足した日時
     */
    public void member(long groupId, long userId, Instant addedAt) {
        jdbc.update(
                "INSERT INTO group_members (group_id, user_id, added_at) VALUES (?, ?, ?)",
                groupId,
                userId,
                OffsetDateTime.ofInstant(addedAt, ZoneOffset.UTC));
    }

    /**
     * グループの数を返す。
     *
     * @param groupId グループの ID
     * @return 行があれば 1、無ければ 0
     */
    public int groupRows(long groupId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM groups WHERE group_id = ?", Integer.class, groupId);
        return count == null ? 0 : count;
    }

    /**
     * グループのメンバーの数を返す。
     *
     * @param groupId グループの ID
     * @return メンバーの行の数
     */
    public int memberRows(long groupId) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM group_members WHERE group_id = ?", Integer.class, groupId);
        return count == null ? 0 : count;
    }
}
