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
package cherry.mastersmith.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * グループ（表 {@code groups}。{@code entities.md} の Group）。利用者をまとめ、ロールの割り当て先になる（割り当ては U4 role が持つ）。
 *
 * <p>JPQL のエンティティ名は {@code UserGroup} とする。{@code Group} は JPQL・HQL の {@code GROUP BY} と重なる名前のため（計画の
 * D-3）。表の名前は {@code groups} のまま。
 *
 * <p>作成・名前の変更・削除の書き込みは {@code group.store} の {@code GroupStore} だけが行う（違反の例外の文に行の値が入るため、
 * メソッドの呼び出しの追跡の対象の外にまとめる。{@code security-design.md} 4.1）。名前と鍵は {@link GroupName} からだけ受け取り、
 * 鍵を自分で作らない（BR1.4）。
 */
@Entity(name = "UserGroup")
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "name_key", nullable = false, length = 256)
    private String nameKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA が使う。 */
    protected Group() {}

    /**
     * 新しいグループを作る（作成の日時と更新の日時は同じ値）。
     *
     * @param name 名前
     * @param now 今の日時（注入した時計の値）
     */
    public Group(GroupName name, Instant now) {
        Objects.requireNonNull(name, "name");
        this.name = name.value();
        this.nameKey = name.key();
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /**
     * 名前を変える（名前・鍵・更新の日時を書き換える。メンバーの足し外しでは呼ばない）。
     *
     * @param newName 新しい名前
     * @param now 今の日時（注入した時計の値）
     */
    public void rename(GroupName newName, Instant now) {
        Objects.requireNonNull(newName, "newName");
        this.name = newName.value();
        this.nameKey = newName.key();
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * グループの ID を返す。
     *
     * @return ID（作成の前は null）
     */
    public Long getGroupId() {
        return groupId;
    }

    /**
     * 名前を返す。
     *
     * @return 名前
     */
    public String getName() {
        return name;
    }

    /**
     * 大文字と小文字を区別しない比べのための鍵を返す。
     *
     * @return 鍵
     */
    public String getNameKey() {
        return nameKey;
    }

    /**
     * 作成の日時を返す。
     *
     * @return 日時
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 更新の日時を返す。
     *
     * @return 日時
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Group[groupId=" + groupId + ", name=" + name + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt
                + "]";
    }
}
