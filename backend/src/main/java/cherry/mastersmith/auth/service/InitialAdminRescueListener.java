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

import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.user.domain.InitialAdminRescuedEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初期管理者の救済の知らせを、救済と同じトランザクションで受けて、ログインの失敗回数を 0 にしてロックを解き、リフレッシュトークンを
 * すべて無効にする（Intent 261004-safety-carryover の FR1.2(c)(e)・FR1.2a）。{@code user} は {@code auth} を知らない
 * （{@link LoginAttemptStateInitializer} と同じ形）。
 *
 * <p>同じトランザクションの中で動くため、救済のどこかが例外を投げれば、ここでの書き換えも巻き戻る（FR1.2a）。テストの失敗の受け手より
 * 先に呼ばれるよう、順を {@code 0} にする。
 *
 * <p><strong>書き換えの口との関係</strong>: 利用者の管理の書き換えの口（{@link RefreshTokenRevocationService}・
 * {@link LockAdministrationService}）は呼ばず、リポジトリを直接使う。その口を呼んでよいのは持ち主のクラスと
 * {@code useradmin.service} だけという決まり（Intent 260930-user-admin の BR7.3・NFR11.2、{@code UserAdminBoundaryArchitectureTest}）
 * を守るため。その結果、起動時の救済は {@code useradmin} を通らない2つ目の書き換えの経路になる（依頼者の決定 D2: A）。
 *
 * <p>ログには利用者 ID と無効にした件数だけを出し、トークンの値・ハッシュは出さない。
 */
@Component
public class InitialAdminRescueListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminRescueListener.class);

    private final LoginAttemptStateRepository loginAttemptStateRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final Clock clock;

    /**
     * 処理を作る。
     *
     * @param loginAttemptStateRepository ロックの状態の DB アクセス
     * @param refreshTokenRepository リフレッシュトークンの DB アクセス
     * @param clock 時計
     */
    public InitialAdminRescueListener(
            LoginAttemptStateRepository loginAttemptStateRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.loginAttemptStateRepository = loginAttemptStateRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /**
     * 救済した利用者の失敗回数を 0 にしてロックを解き（ロックの状態の行が無ければ作って 0 にする）、まだ無効でないリフレッシュトークンを
     * 今の時刻ですべて無効にする。
     *
     * @param event 救済の知らせ
     */
    @Order(0)
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onInitialAdminRescued(InitialAdminRescuedEvent event) {
        long userId = event.userId();
        loginAttemptStateRepository.createIfAbsent(userId);
        loginAttemptStateRepository.update(userId, 0, null);
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId, clock.instant());
        LOGGER.atDebug()
                .addKeyValue("userId", userId)
                .addKeyValue("revoked", revoked)
                .log("初期管理者の救済で、失敗回数を戻しリフレッシュトークンをまとめて無効にしました");
    }
}
