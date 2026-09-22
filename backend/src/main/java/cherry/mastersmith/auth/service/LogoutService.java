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

import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.auth.domain.RefreshTokenValues;
import cherry.mastersmith.auth.domain.TokenExpiry;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログアウト（WF5、BR6.1、BR6.2、BR4.6）。有効なリフレッシュトークンだけを無効にし、{@code LOGGED_OUT} を知らせる。無い・無効
 * なら何もしない。アクセストークンは失効させない。
 */
@Service
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final UserAccountService userAccountService;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    /**
     * ログアウトの処理を作る。
     *
     * @param refreshTokenRepository リフレッシュトークンの DB アクセス
     * @param userAccountService UserAccount の業務処理
     * @param eventPublisher 出来事の知らせ
     * @param clock 時計
     */
    public LogoutService(
            RefreshTokenRepository refreshTokenRepository,
            UserAccountService userAccountService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAccountService = userAccountService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * ログアウトする。
     *
     * @param value Cookie で届いたリフレッシュトークンの値（無ければ null）
     * @param client 送り手の情報
     * @return 無効にしたら true（無い・無効なら false）
     */
    @Transactional
    public boolean logout(RefreshTokenValue value, ClientInfo client) {
        if (value == null) {
            return false;
        }
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(RefreshTokenValues.hash(value));
        Instant now = clock.instant();
        if (found.isEmpty()
                || found.get().getRevokedAt() != null
                || !TokenExpiry.isValid(now, found.get().getExpiresAt())) {
            return false;
        }
        RefreshToken token = found.get();
        if (refreshTokenRepository.revokeIfActive(token.getTokenId(), now) != 1) {
            return false;
        }
        Optional<UserSummary> user = userAccountService.findById(token.getUserId());
        eventPublisher.publishEvent(AuthenticationEvent.of(
                AuthenticationEventType.LOGGED_OUT,
                now,
                user.map(UserSummary::email).orElse(null),
                token.getUserId(),
                null,
                client));
        return true;
    }
}
