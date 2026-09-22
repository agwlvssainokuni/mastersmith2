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
package cherry.mastersmith.auth.domain;

import jakarta.persistence.Column;
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
 * リフレッシュトークン（表 {@code refresh_tokens}）。値は持たず、SHA-256 のハッシュ（32 バイト）だけを持つ（BR5.1）。
 *
 * <p>利用者は ID だけで指し、{@code user} のエンティティを参照しない（ADR-001）。無効化は repository の条件付きの更新で行う
 * （BR5.6）。
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "token_hash", nullable = false, length = 32)
    private byte[] tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** JPA が使う。 */
    protected RefreshToken() {}

    /**
     * 新しいリフレッシュトークンの行を作る。
     *
     * @param userId 利用者ID
     * @param tokenHash 値の SHA-256 のハッシュ（32 バイト）
     * @param issuedAt 発行の日時
     * @param expiresAt 有効期限
     */
    public RefreshToken(long userId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * トークンの行の ID を返す。
     *
     * @return ID（保存前は null）
     */
    public Long getTokenId() {
        return tokenId;
    }

    /**
     * 利用者IDを返す。
     *
     * @return 利用者ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 値のハッシュの写しを返す。
     *
     * @return ハッシュ（32 バイト）
     */
    public byte[] getTokenHash() {
        return tokenHash.clone();
    }

    /**
     * 発行の日時を返す。
     *
     * @return 発行の日時
     */
    public Instant getIssuedAt() {
        return issuedAt;
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
     * 無効にした日時を返す。
     *
     * @return 無効にした日時（有効なら null）
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }
}
