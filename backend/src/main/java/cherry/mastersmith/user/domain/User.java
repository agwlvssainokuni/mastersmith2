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
import jakarta.persistence.Convert;
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
 *
 * <p>氏名と表示の設定の4列（V7、ADR-003）を持つ。書き換えはエンティティの変更の検出では行わず、4列だけ・パスワードのハッシュだけを
 * 書き換える更新の問い合わせ（{@code user.repository}）で行う（全列を書いて相手の列を古い値で上書きしないため。BR3.4）。
 *
 * <p>停止の状態（V9、Intent 260930-user-admin の U1）を持つ。作るときは停止していない（false）。外から書き換えるメソッドは持たず、
 * 書き換えは {@code user.repository} の停止の列だけの更新の問い合わせで行う（NFR1.4）。
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

    @Column(name = "display_name", nullable = false, length = 508)
    private String displayName;

    @Convert(converter = LanguageConverter.class)
    @Column(name = "language", nullable = false, length = 2)
    private Language language;

    @Convert(converter = ThemeConverter.class)
    @Column(name = "theme", nullable = false, length = 6)
    private Theme theme;

    @Convert(converter = FontSizeConverter.class)
    @Column(name = "font_size", nullable = false, length = 2)
    private FontSize fontSize;

    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    /** JPA が使う。 */
    protected User() {}

    /**
     * 新しい利用者を作る。
     *
     * @param email メールアドレス（小文字にそろえた値）
     * @param passwordHash パスワードのハッシュ
     * @param adminFlag 管理者か
     * @param createdAt 作成の日時
     * @param preferences 氏名と表示の設定
     */
    public User(String email, String passwordHash, boolean adminFlag, Instant createdAt, Preferences preferences) {
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.adminFlag = adminFlag;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(preferences, "preferences");
        this.displayName = preferences.displayName();
        this.language = preferences.language();
        this.theme = preferences.theme();
        this.fontSize = preferences.fontSize();
        this.suspended = false;
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

    /**
     * 氏名を返す。
     *
     * @return 氏名（前後の空白を除いた値）
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 言語を返す。
     *
     * @return 言語
     */
    public Language getLanguage() {
        return language;
    }

    /**
     * テーマを返す。
     *
     * @return テーマ
     */
    public Theme getTheme() {
        return theme;
    }

    /**
     * 文字の大きさを返す。
     *
     * @return 文字の大きさ
     */
    public FontSize getFontSize() {
        return fontSize;
    }

    /**
     * 利用停止中かを返す。書き換えは {@code user.repository} の停止の列だけの更新の問い合わせで行う。
     *
     * @return 利用停止中なら true
     */
    public boolean isSuspended() {
        return suspended;
    }

    /**
     * 氏名と表示の設定の4つの組を返す。
     *
     * @return 4つの組
     */
    public Preferences getPreferences() {
        return new Preferences(displayName, language, theme, fontSize);
    }
}
