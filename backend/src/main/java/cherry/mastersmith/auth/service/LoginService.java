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
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.LockDecision;
import cherry.mastersmith.auth.domain.LockPolicy;
import cherry.mastersmith.auth.domain.LockState;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import cherry.mastersmith.auth.domain.LoginOutcome;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.auth.domain.RefreshTokenValues;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.user.service.PasswordVerification;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログイン（WF2。手順の順番は NFR Design の security-design 8章・reliability-design 1章）。
 *
 * <ol>
 *   <li>利用者の検索1回と照合1回（トランザクションと排他の外）
 *   <li>1回の短いトランザクションで、利用者の行（いなければダミーの行）を排他つきで読み、判定し、明示の更新を1回行う。成功なら
 *       トークンを発行し、リフレッシュトークンを保存する（ログインのたびに新しい行。BR5.7）
 *   <li>同じトランザクションの中で出来事を知らせる（受け取り側は確定の後に記録する。BR7.3）
 *   <li>失敗は理由によらず {@code AUTHENTICATION_FAILED}（BR2.4）
 * </ol>
 */
@Service
public class LoginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginService.class);

    private final UserAccountService userAccountService;

    private final LoginAttemptStateRepository attemptRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final AccessTokenService accessTokenService;

    private final ApplicationEventPublisher eventPublisher;

    private final AuthProperties properties;

    private final Clock clock;

    private final TransactionTemplate transaction;

    /**
     * ログインの処理を作る。
     *
     * @param userAccountService UserAccount の業務処理
     * @param attemptRepository ロックの状態の DB アクセス
     * @param refreshTokenRepository リフレッシュトークンの DB アクセス
     * @param accessTokenService アクセストークンの発行
     * @param eventPublisher 出来事の知らせ
     * @param properties 認証の設定
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public LoginService(
            UserAccountService userAccountService,
            LoginAttemptStateRepository attemptRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            ApplicationEventPublisher eventPublisher,
            AuthProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.userAccountService = userAccountService;
        this.attemptRepository = attemptRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * ログインする。
     *
     * @param command メールアドレスとパスワード
     * @param client 送り手の情報
     * @return 発行したトークンと利用者の要約
     * @throws BusinessException 失敗のとき（理由によらず {@code AUTHENTICATION_FAILED}）
     */
    public IssuedTokens login(LoginCommand command, ClientInfo client) {
        PasswordVerification verification = userAccountService.verifyPassword(command.email(), command.password());
        IssuedTokens tokens = transaction.execute(status -> decide(verification, client));
        if (tokens == null) {
            throw new BusinessException(AuthProblemTypes.AUTHENTICATION_FAILED);
        }
        return tokens;
    }

    /** 排他つきで状態を読み、判定し、書き込む。成功ならトークンを返し、失敗なら null を返す（トランザクションは確定する）。 */
    private IssuedTokens decide(PasswordVerification verification, ClientInfo client) {
        UserSummary user = verification.user();
        LoginAttemptState row = user == null ? attemptRepository.lockDummyForUpdate() : lockUserRow(user.userId());
        LockState current = new LockState(row.getConsecutiveFailures(), row.getLockedUntil());
        Instant now = clock.instant();
        if (user == null) {
            attemptRepository.update(row.getSubjectId(), current.consecutiveFailures(), current.lockedUntil());
            publishFailure(verification.email(), null, LoginFailureReason.USER_NOT_FOUND, now, client);
            return null;
        }
        LockDecision decision = LockPolicy.decide(
                current,
                verification.matched(),
                now,
                properties.lock().threshold(),
                properties.lock().duration());
        LockState next = decision.next();
        attemptRepository.update(user.userId(), next.consecutiveFailures(), next.lockedUntil());
        if (decision.lockedNow()) {
            LOGGER.atInfo().addKeyValue("userId", user.userId()).log("アカウントをロックしました");
        }
        if (decision.outcome() != LoginOutcome.SUCCEEDED) {
            LoginFailureReason reason = decision.outcome() == LoginOutcome.ACCOUNT_LOCKED
                    ? LoginFailureReason.ACCOUNT_LOCKED
                    : LoginFailureReason.PASSWORD_MISMATCH;
            publishFailure(verification.email(), user.userId(), reason, now, client);
            return null;
        }
        IssuedTokens tokens = issueTokens(user, now);
        eventPublisher.publishEvent(AuthenticationEvent.of(
                AuthenticationEventType.LOGIN_SUCCEEDED, now, verification.email(), user.userId(), null, client));
        return tokens;
    }

    private LoginAttemptState lockUserRow(long userId) {
        return attemptRepository.lockForUpdate(userId).orElseGet(() -> {
            attemptRepository.createIfAbsent(userId);
            return attemptRepository
                    .lockForUpdate(userId)
                    .orElseThrow(() -> new IllegalStateException("ロックの状態の行を作れませんでした"));
        });
    }

    private void publishFailure(String email, Long userId, LoginFailureReason reason, Instant now, ClientInfo client) {
        eventPublisher.publishEvent(
                AuthenticationEvent.of(AuthenticationEventType.LOGIN_FAILED, now, email, userId, reason, client));
    }

    /**
     * アクセストークンを発行し、新しいリフレッシュトークンを保存する（ログインと更新で共通）。
     *
     * @param user 利用者の要約
     * @param now 現在時刻
     * @return 発行したトークン
     */
    public IssuedTokens issueTokens(UserSummary user, Instant now) {
        IssuedAccessToken accessToken = accessTokenService.issue(user.userId());
        RefreshTokenValue refreshToken = RefreshTokenValues.generate();
        refreshTokenRepository.save(new RefreshToken(
                user.userId(), RefreshTokenValues.hash(refreshToken), now, now.plus(properties.refreshTokenTtl())));
        return new IssuedTokens(accessToken, refreshToken, properties.refreshTokenTtl(), user);
    }
}
