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
package cherry.mastersmith.auth.service;

import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 利用者のリフレッシュトークンのまとめての無効化（契約 C1、Intent 260930-user-admin の U1、BR5.1〜BR5.3）。
 *
 * <p>利用を止める操作（U3）が、停止の状態の書き換えと同じトランザクションで呼ぶ。監査の出来事は出さない（止める操作の監査は呼び出し元
 * の受け持ち）。ログには利用者 ID と件数だけを出し、トークンの値・ハッシュは出さない。
 */
@Service
public class RefreshTokenRevocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenRevocationService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    private final Clock clock;

    /**
     * まとめての無効化の処理を作る。
     *
     * @param refreshTokenRepository リフレッシュトークンの DB アクセス
     * @param clock 時計
     */
    public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /**
     * 利用者のまだ無効でないリフレッシュトークンを、今の時刻ですべて無効にする（期限切れの行も含む）。既に無効の行は変えない。
     *
     * <p>呼び出し元のトランザクションの中でだけ呼べる（無ければ {@code IllegalTransactionStateException}）。呼び出し元が巻き戻すと、
     * 無効化も残らない。
     *
     * @param userId 利用者 ID
     * @return 無効にした行の数（0 件も成功）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RevokeAllResult revokeAllRefreshTokens(long userId) {
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant());
        LOGGER.atDebug()
                .addKeyValue("userId", userId)
                .addKeyValue("revoked", revoked)
                .log("リフレッシュトークンをまとめて無効にしました");
        return new RevokeAllResult(revoked);
    }
}
