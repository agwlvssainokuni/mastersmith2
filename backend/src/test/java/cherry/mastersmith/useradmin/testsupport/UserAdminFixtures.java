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
package cherry.mastersmith.useradmin.testsupport;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.service.CreateUserResult;
import cherry.mastersmith.user.service.NewUser;
import cherry.mastersmith.user.service.UserAccountService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の管理の結合テストで、利用者とロックの状態の行を用意するテストの補助（Intent 260930-user-admin の U3）。
 *
 * <p>利用者は契約 C2 の {@code createUser} で作り（登録した日時は注入した時計の今）、停止は契約 C1 の口（{@link TestUserSuspension}）で
 * 入れる。ロックの状態の行だけは、ログインの失敗を重ねずに状態を作るため JDBC で書く（{@code users} は JDBC で書き換えない）。
 * メールアドレスは予約のドメイン {@code example.com} だけを使う。
 */
public final class UserAdminFixtures {

    /** テストで使うパスワード（明らかにテスト用と分かる値）。 */
    public static final String PASSWORD = "利用者の管理のテスト-0000";

    private final UserAccountService userAccountService;

    private final JdbcTemplate jdbc;

    private final TestUserSuspension suspension;

    private final AuthApi auth;

    /**
     * 作る。
     *
     * @param userAccountService 利用者の業務処理
     * @param revocationService リフレッシュトークンのまとめての無効化
     * @param transactionManager トランザクションの管理
     * @param jdbc 内部DB への問い合わせ
     * @param port アプリの待ち受けの番号
     */
    public UserAdminFixtures(
            UserAccountService userAccountService,
            RefreshTokenRevocationService revocationService,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbc,
            int port) {
        this.userAccountService = userAccountService;
        this.jdbc = jdbc;
        this.suspension = new TestUserSuspension(
                new TransactionTemplate(transactionManager), userAccountService, revocationService);
        this.auth = new AuthApi(port);
    }

    /**
     * テストごとの印（乱数の英数字）を作る。検索で自分が作った利用者だけに当てるために使う。
     *
     * @return 印
     */
    public static String marker() {
        return "m" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 利用者を作る（言語 ja・テーマ system・文字の大きさ md）。
     *
     * @param email メールアドレス
     * @param displayName 氏名
     * @param admin 管理者か
     * @return 利用者 ID
     */
    public long create(String email, String displayName, boolean admin) {
        CreateUserResult result = userAccountService.createUser(
                new NewUser(email, displayName, new Password(PASSWORD), Language.JA, Theme.SYSTEM, FontSize.MD, admin));
        if (result instanceof CreateUserResult.Created created) {
            return created.userId();
        }
        throw new IllegalStateException("利用者を作れませんでした: " + result.getClass().getSimpleName());
    }

    /**
     * ログインしてアクセストークンを得る。
     *
     * @param email メールアドレス
     * @return アクセストークン
     */
    public String login(String email) {
        return AuthApi.accessToken(auth.login(email, PASSWORD));
    }

    /**
     * 利用者を止め、リフレッシュトークンをまとめて無効にする。
     *
     * @param userId 利用者 ID
     */
    public void suspend(long userId) {
        suspension.suspend(userId);
    }

    /**
     * ロックの状態の行を入れる（あれば置き換える）。
     *
     * @param userId 利用者 ID
     * @param failures 連続の失敗回数
     * @param lockedUntil 解除の予定の時刻（無ければ null）
     */
    public void lockState(long userId, int failures, Instant lockedUntil) {
        jdbc.update(
                "MERGE INTO login_attempt_states (subject_id, consecutive_failures, locked_until) KEY (subject_id)"
                        + " VALUES (?, ?, ?)",
                userId,
                failures,
                lockedUntil == null ? null : OffsetDateTime.ofInstant(lockedUntil, ZoneOffset.UTC));
    }
}
