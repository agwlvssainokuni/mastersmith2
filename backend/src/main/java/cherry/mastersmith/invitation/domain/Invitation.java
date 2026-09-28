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
package cherry.mastersmith.invitation.domain;

import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.LanguageConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 招待（表 {@code invitations}、V8。{@code entities.md} の Invitation）。利用者の表とは別に持ち、登録の完了まで利用者を作らない
 * （ADR-001）。
 *
 * <p>生成列 {@code pending_email}（同じメールアドレスの招待中を1件に限る一意の制約の列）は持たない。書き込みで値を渡さず DB が計算する
 * （Hibernate の {@code validate} は表にある余分な列を見ない。{@code reliability-design.md} 2.1）。トークンの値・招待の URL は持たず、
 * トークンのハッシュだけを持つ。
 *
 * <p>状態の移り変わりは {@link #replace}・{@link #resend}・{@link #cancel}・{@link #complete} だけで行い、どれも招待中でなければ想定外の
 * 誤りとする（呼び出し元が行の排他と状態の確かめを先に済ませる）。文字列化は {@code Object} の既定のまま（項目を出さない）。
 */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    private Long invitationId;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @Convert(converter = LanguageConverter.class)
    @Column(name = "language", nullable = false, updatable = false, length = 2)
    private Language language;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "token_hash", nullable = false, length = 32)
    private byte[] tokenHash;

    @Column(name = "invited_by_user_id", nullable = false, updatable = false)
    private long invitedByUserId;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Convert(converter = SendResultConverter.class)
    @Column(name = "send_result", nullable = false, length = 8)
    private SendResult sendResult;

    @Convert(converter = InvitationStateConverter.class)
    @Column(name = "state", nullable = false, length = 16)
    private InvitationState state;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "completed_user_id")
    private Long completedUserId;

    /** JPA が使う。 */
    protected Invitation() {}

    /**
     * 新しい招待を作る（状態と送信の結果は招待中・送信中。BR2.6）。
     *
     * @param email 正規化した招待先のメールアドレス
     * @param language 招待の言語
     * @param tokenHash トークンのハッシュ（32 バイト）
     * @param invitedByUserId 招待した管理者の利用者 ID
     * @param invitedAt 招待した時点
     * @param expiresAt 有効期限（招待した時点より後）
     */
    public Invitation(
            InvitationEmail email,
            Language language,
            byte[] tokenHash,
            long invitedByUserId,
            Instant invitedAt,
            Instant expiresAt) {
        this.email = Objects.requireNonNull(email, "email").value();
        this.language = Objects.requireNonNull(language, "language");
        this.tokenHash = requireHash(tokenHash);
        this.invitedByUserId = invitedByUserId;
        this.invitedAt = Objects.requireNonNull(invitedAt, "invitedAt");
        this.expiresAt = requireAfter(expiresAt, invitedAt);
        this.sendResult = SendResult.PENDING;
        this.state = InvitationState.PENDING;
    }

    private static byte[] requireHash(byte[] hash) {
        Objects.requireNonNull(hash, "tokenHash");
        if (hash.length != 32) {
            throw new IllegalArgumentException("トークンのハッシュは 32 バイトです");
        }
        return hash.clone();
    }

    private static Instant requireAfter(Instant expiresAt, Instant from) {
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(from)) {
            throw new IllegalArgumentException("有効期限は起点より後です");
        }
        return expiresAt;
    }

    private void requirePending() {
        if (state != InvitationState.PENDING) {
            throw new IllegalStateException("招待中でない招待は変えられません");
        }
    }

    /**
     * 期限切れの招待中を置き換え済みにする（BR2.4）。
     *
     * @param now 今
     */
    public void replace(Instant now) {
        requirePending();
        this.state = InvitationState.REPLACED;
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 送り直す（トークン・有効期限・送信の結果だけを置き換える。BR6.1）。
     *
     * @param newTokenHash 新しいトークンのハッシュ
     * @param now 今
     * @param newExpiresAt 新しい有効期限（今より後）
     */
    public void resend(byte[] newTokenHash, Instant now, Instant newExpiresAt) {
        requirePending();
        this.tokenHash = requireHash(newTokenHash);
        this.expiresAt = requireAfter(newExpiresAt, Objects.requireNonNull(now, "now"));
        this.sendResult = SendResult.PENDING;
    }

    /**
     * 取り消す（トークンのハッシュは残す。BR6.2）。
     *
     * @param now 今
     */
    public void cancel(Instant now) {
        requirePending();
        this.state = InvitationState.CANCELLED;
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 登録を完了した状態にする（BR7.3）。
     *
     * @param userId 作った利用者の ID
     * @param now 今
     */
    public void complete(long userId, Instant now) {
        requirePending();
        this.state = InvitationState.COMPLETED;
        this.completedUserId = userId;
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 今の時点で有効かを返す（BR3.3）。
     *
     * @param now 今
     * @return 有効なら true
     */
    public boolean isValidAt(Instant now) {
        return InvitationValidity.isValid(state, expiresAt, now);
    }

    /**
     * 招待の ID を返す。
     *
     * @return ID（保存の前は null）
     */
    public Long getInvitationId() {
        return invitationId;
    }

    /**
     * 招待先のメールアドレスを返す。
     *
     * @return 正規化したメールアドレス（伏せ字の値の型）
     */
    public InvitationEmail getEmail() {
        return new InvitationEmail(email);
    }

    /**
     * 招待の言語を返す。
     *
     * @return 言語
     */
    public Language getLanguage() {
        return language;
    }

    /**
     * トークンのハッシュの写しを返す。
     *
     * @return ハッシュ（32 バイト）
     */
    public byte[] getTokenHash() {
        return tokenHash.clone();
    }

    /**
     * 招待した管理者の利用者 ID を返す。
     *
     * @return 利用者 ID
     */
    public long getInvitedByUserId() {
        return invitedByUserId;
    }

    /**
     * 招待した時点を返す。
     *
     * @return 時点
     */
    public Instant getInvitedAt() {
        return invitedAt;
    }

    /**
     * 有効期限を返す。
     *
     * @return 有効期限
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * 送信の結果を返す。
     *
     * @return 送信の結果
     */
    public SendResult getSendResult() {
        return sendResult;
    }

    /**
     * 状態を返す。
     *
     * @return 状態
     */
    public InvitationState getState() {
        return state;
    }

    /**
     * 終わった時点を返す。
     *
     * @return 時点（招待中は null）
     */
    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * 登録の完了で作った利用者の ID を返す。
     *
     * @return 利用者 ID（完了していなければ null）
     */
    public Long getCompletedUserId() {
        return completedUserId;
    }
}
