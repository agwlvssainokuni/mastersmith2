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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
 *
 * <p>利用者のロックの状態の行が無いとき（初めてのログインなど）は、判定のトランザクションでは何も書かず、出来事も知らせずに終え、
 * 別の短いトランザクションで行を作ってから判定をやり直す。同じ利用者の初めてのログインが同時に来て、行の作成が重複で失敗したときは
 * 「既にある」として扱う（判定のトランザクションの中で重複を受け止めると、トランザクションが巻き戻し専用になり続けられないため）。
 * トランザクションは順に行うため、同時に使う接続は1本のまま。
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
        Decision decision = transaction.execute(status -> decide(verification, client));
        if (decision != null && decision.rowMissing()) {
            long userId = verification.user().userId();
            createRow(userId);
            decision = transaction.execute(status -> decide(verification, client));
            if (decision != null && decision.rowMissing()) {
                throw new IllegalStateException("ロックの状態の行を作れませんでした: userId=" + userId);
            }
        }
        if (decision == null || decision.tokens() == null) {
            throw new BusinessException(AuthProblemTypes.AUTHENTICATION_FAILED);
        }
        return decision.tokens();
    }

    /**
     * 利用者のロックの状態の行を、判定とは別の短いトランザクションで作る。同時の別のログインが先に作っていて重複になったときは、
     * 「既にある」として進む。
     */
    private void createRow(long userId) {
        try {
            transaction.executeWithoutResult(status -> attemptRepository.createIfAbsent(userId));
        } catch (DataIntegrityViolationException e) {
            LOGGER.atDebug().addKeyValue("userId", userId).log("ロックの状態の行は同時の別のログインが先に作りました");
        }
    }

    /**
     * 排他つきで状態を読み、判定し、書き込む（トランザクションは確定する）。利用者の行が無ければ、何も書かず出来事も知らせずに
     * {@link Decision#ROW_MISSING} を返す。
     */
    private Decision decide(PasswordVerification verification, ClientInfo client) {
        UserSummary user = verification.user();
        LoginAttemptState row;
        if (user == null) {
            row = attemptRepository.lockDummyForUpdate();
        } else {
            Optional<LoginAttemptState> found = attemptRepository.lockForUpdate(user.userId());
            if (found.isEmpty()) {
                return Decision.ROW_MISSING;
            }
            row = found.get();
        }
        LockState current = new LockState(row.getConsecutiveFailures(), row.getLockedUntil());
        Instant now = clock.instant();
        if (user == null) {
            attemptRepository.update(row.getSubjectId(), current.consecutiveFailures(), current.lockedUntil());
            publishFailure(verification.email(), null, LoginFailureReason.USER_NOT_FOUND, now, client);
            return Decision.FAILED;
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
            return Decision.FAILED;
        }
        IssuedTokens tokens = issueTokens(user, now);
        eventPublisher.publishEvent(AuthenticationEvent.of(
                AuthenticationEventType.LOGIN_SUCCEEDED, now, verification.email(), user.userId(), null, client));
        return new Decision(false, tokens);
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

    /**
     * 判定の1回の結果。
     *
     * @param rowMissing 利用者のロックの状態の行が無く、判定しなかったとき true
     * @param tokens 成功のときに発行したトークン（失敗・行が無いときは null）
     */
    private record Decision(boolean rowMissing, IssuedTokens tokens) {

        /** 利用者の行が無く、判定しなかった。 */
        static final Decision ROW_MISSING = new Decision(true, null);

        /** 判定して失敗した。 */
        static final Decision FAILED = new Decision(false, null);
    }
}
