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
package cherry.mastersmith.auth.repository;

import cherry.mastersmith.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** リフレッシュトークンの表の DB アクセス（BR5.3〜BR5.6、NFR1.7、NFR9.2）。保存は {@link JpaRepository} の操作を使う。 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 1回の削除の件数の上限。 */
    int DELETE_BATCH_SIZE = 1000;

    /**
     * 値のハッシュで探す。
     *
     * @param tokenHash 値の SHA-256 のハッシュ
     * @return トークンの行（無ければ空）
     */
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /**
     * まだ無効でないことを条件に無効にする（同時の更新でも成功は1つだけ。BR5.6）。
     *
     * @param tokenId トークンの行の ID
     * @param revokedAt 無効にする日時
     * @return 更新した行の数（1 なら成功、0 なら既に無効）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :revokedAt where t.tokenId = :tokenId and t.revokedAt is null")
    int revokeIfActive(@Param("tokenId") long tokenId, @Param("revokedAt") Instant revokedAt);

    /**
     * 有効期限が指定の日時より前の行（無効・期限切れで、保存の日数を過ぎた行）を、件数の上限までまとめて消す。
     *
     * @param cutoff この日時より前に期限を迎えた行を消す
     * @param limit 1回の件数の上限
     * @return 消した行の数
     */
    @Modifying(clearAutomatically = true)
    @Query(
            value = "DELETE FROM refresh_tokens WHERE token_id IN (SELECT token_id FROM refresh_tokens"
                    + " WHERE expires_at < :cutoff ORDER BY token_id FETCH FIRST :limit ROWS ONLY)",
            nativeQuery = true)
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
