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
package cherry.mastersmith.role.testsupport;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.service.CreateUserResult;
import cherry.mastersmith.user.service.NewUser;
import cherry.mastersmith.user.service.UserAccountService;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ロールの管理の API の結合テストの主体（管理者・管理者の印を持たない利用者・停止中の管理者、B5 で足した「要る権限だけを欠く利用者」）を作る
 * 手伝い（Intent 261004-role-menu の U4、計画の 7.3・8.3）。利用者は本番の作成の口で作り、メールアドレスは予約のドメイン（{@code example.com}）だけ、氏名は
 * 明らかな見本の値にする。
 */
public final class RoleActors {

    /** テストで使うパスワード（明らかにテスト用と分かる値）。 */
    public static final String PASSWORD = "ロールの管理のテスト-0000";

    /**
     * テストの主体。
     *
     * @param userId 利用者 ID
     * @param email メールアドレス
     * @param token アクセストークン
     */
    public record Actor(long userId, String email, String token) {}

    private final UserAccountService userAccountService;

    private final TestUserSuspension suspension;

    private final AuthApi auth;

    /**
     * 作る。
     *
     * @param userAccountService UserAccount の口
     * @param revocationService リフレッシュトークンのまとめての無効化
     * @param transactionManager トランザクションの管理
     * @param port アプリのポート
     */
    public RoleActors(
            UserAccountService userAccountService,
            RefreshTokenRevocationService revocationService,
            PlatformTransactionManager transactionManager,
            int port) {
        this.userAccountService = userAccountService;
        this.suspension = new TestUserSuspension(
                new TransactionTemplate(transactionManager), userAccountService, revocationService);
        this.auth = new AuthApi(port);
    }

    /**
     * 管理者を作り、ログインする。
     *
     * @return 主体
     */
    public Actor admin() {
        return login(create("role-admin", "見本 管理者", true));
    }

    /**
     * 管理者の印を持たない利用者を作り、ログインする。
     *
     * @return 主体
     */
    public Actor member() {
        return login(create("role-member", "見本 一般", false));
    }

    /** {@link #adminFlagMissingFullRole(RoleFixtures)} のロールが FULL・CREATE と DELETE を可にするスキーマ（テストの DSL のすべて）。 */
    public static final List<String> FULL_SCHEMAS = List.of("SALES", "HR", RoleDslFixture.SYMBOL_SCHEMA);

    /**
     * 管理者の印だけを欠く利用者を作り、ログインする（group の読み直しの R-04、計画の 13節 Q3: A）。テストの DSL のすべてのスキーマに FULL・
     * CREATE と DELETE を可にしたロールを割り当て、作業ロールに選んだ利用者で、業務データの権限はすべて持つ。
     *
     * @param fixtures 内部DB の行を作る手伝い
     * @return 主体
     */
    public Actor adminFlagMissingFullRole(RoleFixtures fixtures) {
        Actor actor = member();
        long roleId = fixtures.role(RoleFixtures.uniqueName("全権限"));
        for (String schema : FULL_SCHEMAS) {
            fixtures.setting(
                    roleId, PermissionTarget.schema(schema), new PermissionValues(MainPermission.FULL, true, true));
        }
        fixtures.assignUser(roleId, actor.userId());
        fixtures.selectWorkRole(actor.userId(), roleId);
        return actor;
    }

    /**
     * 利用者の利用を止める（トークンは止める前に出したもののまま）。
     *
     * @param userId 利用者 ID
     */
    public void suspend(long userId) {
        suspension.suspend(userId);
    }

    /**
     * ログインした後に利用を止めた管理者を作る（トークンは止める前に出したもの）。
     *
     * @return 主体
     */
    public Actor suspendedAdmin() {
        Actor actor = admin();
        suspension.suspend(actor.userId());
        return actor;
    }

    /**
     * ログインしない利用者（管理者の印なし）を作る。
     *
     * @param displayName 氏名（見本の値）
     * @return 主体（ログインしない。トークンは null）
     */
    public Actor user(String displayName) {
        return create("role-user", displayName, false);
    }

    /**
     * 管理者の印を持たない利用者を作り、ログインする（氏名を指定する）。
     *
     * @param displayName 氏名（見本の値）
     * @return 主体
     */
    public Actor member(String displayName) {
        return login(create("role-member", displayName, false));
    }

    private Actor create(String label, String displayName, boolean admin) {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        CreateUserResult result = userAccountService.createUser(
                new NewUser(email, displayName, new Password(PASSWORD), Language.JA, Theme.SYSTEM, FontSize.MD, admin));
        if (result instanceof CreateUserResult.Created created) {
            return new Actor(created.userId(), email, null);
        }
        throw new IllegalStateException("利用者を作れませんでした: " + result.getClass().getSimpleName());
    }

    private Actor login(Actor actor) {
        return new Actor(actor.userId(), actor.email(), AuthApi.accessToken(auth.login(actor.email(), PASSWORD)));
    }
}
