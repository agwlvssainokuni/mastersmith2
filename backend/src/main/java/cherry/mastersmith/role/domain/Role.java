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
package cherry.mastersmith.role.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * ロール（表 {@code roles}。{@code entities.md} の Role）。業務データの権限の設定のまとまりで、管理の権限は与えない（要件 C1）。
 * 組み込みのロールは無い。
 *
 * <p>JPQL のエンティティ名は {@code Role}（計画の D-2）。作成・名前の変更・削除・更新の日時の書き換えは {@code role.store} の
 * {@code RoleStore} だけが行う（違反の例外の文に行の値が入るため、メソッドの呼び出しの追跡の対象の外にまとめる。
 * {@code security-design.md} 4.1）。名前と鍵は {@link RoleName} からだけ受け取り、鍵を自分で作らない（BR1.4）。
 */
@Entity(name = "Role")
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "name_key", nullable = false, length = 256)
    private String nameKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA が使う。 */
    protected Role() {}

    /**
     * 新しいロールを作る（作成の日時と更新の日時は同じ値）。
     *
     * @param name 名前
     * @param now 今の日時（注入した時計の値）
     */
    public Role(RoleName name, Instant now) {
        Objects.requireNonNull(name, "name");
        this.name = name.value();
        this.nameKey = name.key();
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /**
     * 名前を変える（名前・鍵・更新の日時を書き換える）。
     *
     * @param newName 新しい名前
     * @param now 今の日時（注入した時計の値）
     */
    public void rename(RoleName newName, Instant now) {
        Objects.requireNonNull(newName, "newName");
        this.name = newName.value();
        this.nameKey = newName.key();
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 中身（権限の設定）が変わったことを記録する（更新の日時だけを書き換える。権限の保存・消す・import の置き換え）。
     *
     * @param now 今の日時（注入した時計の値）
     */
    public void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * ロールの ID を返す。
     *
     * @return ID（作成の前は null）
     */
    public Long getRoleId() {
        return roleId;
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

    /** ID だけを出す（メソッドの呼び出しの追跡で文字列にされるため。計画の 7.2）。 */
    @Override
    public String toString() {
        return "Role[roleId=" + roleId + "]";
    }
}
