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

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.auth.domain.RefreshTokenValues;
import cherry.mastersmith.auth.domain.TokenExpiry;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * トークンの更新（WF4、BR5.3〜BR5.6）。使ったリフレッシュトークンを条件付きで無効にし、新しいリフレッシュトークン（今の時刻から
 * 有効期限を数える）とアクセストークンを発行する。無効化と新しい行の保存は同じトランザクション。ほかのトークンは無効にしない。
 */
@Service
public class TokenRefreshService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final UserAccountService userAccountService;

    private final LoginService loginService;

    private final Clock clock;

    /**
     * 更新の処理を作る。
     *
     * @param refreshTokenRepository リフレッシュトークンの DB アクセス
     * @param userAccountService UserAccount の業務処理
     * @param loginService トークンの発行（ログインと共通）
     * @param clock 時計
     */
    public TokenRefreshService(
            RefreshTokenRepository refreshTokenRepository,
            UserAccountService userAccountService,
            LoginService loginService,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAccountService = userAccountService;
        this.loginService = loginService;
        this.clock = clock;
    }

    /**
     * リフレッシュトークンで更新する。
     *
     * @param value Cookie で届いたリフレッシュトークンの値（無ければ null）
     * @return 新しいトークンと利用者の要約
     * @throws BusinessException 無い・存在しない・使用済み・期限切れ・同時の更新に負けたとき（理由によらず
     *     {@code REFRESH_FAILED}）
     */
    @Transactional
    public IssuedTokens refresh(RefreshTokenValue value) {
        if (value == null) {
            throw failed();
        }
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(RefreshTokenValues.hash(value))
                .orElseThrow(TokenRefreshService::failed);
        Instant now = clock.instant();
        if (token.getRevokedAt() != null || !TokenExpiry.isValid(now, token.getExpiresAt())) {
            throw failed();
        }
        if (refreshTokenRepository.revokeIfActive(token.getTokenId(), now) != 1) {
            throw failed();
        }
        UserSummary user = userAccountService.findById(token.getUserId()).orElseThrow(TokenRefreshService::failed);
        return loginService.issueTokens(user, now);
    }

    private static BusinessException failed() {
        return new BusinessException(AuthProblemTypes.REFRESH_FAILED);
    }
}
