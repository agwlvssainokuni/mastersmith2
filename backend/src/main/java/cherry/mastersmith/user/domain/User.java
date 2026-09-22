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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * 利用者（表 {@code users}）。パスワードはハッシュ（bcrypt の形式）だけを持つ（BR1.5）。
 *
 * <p>パスワードのハッシュは {@code user} パッケージの中だけで読み、外へ出さない（ADR-001）。ほかの機能には
 * {@code user.service} の利用者の要約を渡す。文字列化は既定のまま（中身を出さない）。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "admin_flag", nullable = false)
    private boolean adminFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA が使う。 */
    protected User() {}

    /**
     * 新しい利用者を作る。
     *
     * @param email メールアドレス（小文字にそろえた値）
     * @param passwordHash パスワードのハッシュ
     * @param adminFlag 管理者か
     * @param createdAt 作成の日時
     */
    public User(String email, String passwordHash, boolean adminFlag, Instant createdAt) {
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.adminFlag = adminFlag;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 利用者IDを返す。
     *
     * @return 利用者ID（保存前は null）
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * メールアドレスを返す。
     *
     * @return メールアドレス（小文字にそろえた値）
     */
    public String getEmail() {
        return email;
    }

    /**
     * パスワードのハッシュを返す。{@code user} パッケージの中だけで使う（構造の検査で確かめる）。
     *
     * @return パスワードのハッシュ
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 管理者かを返す。
     *
     * @return 管理者なら true
     */
    public boolean isAdminFlag() {
        return adminFlag;
    }

    /**
     * 作成の日時を返す。
     *
     * @return 作成の日時
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
