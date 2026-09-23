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
package cherry.mastersmith.access.testsupport;

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.util.UUID;

/**
 * 結合テストで、管理者・管理者でない利用者を作り、ログインしてアクセストークンを得るテストの補助（計画の C9）。
 *
 * <p>メールアドレスはテストごとに一意にし、実行の順番に依存させない。パスワードは明らかにテスト用と分かる 12 文字以上の値で、
 * 本物らしい秘密情報をソースに書かない。
 */
public final class AdminTestUsers {

    /** テストで使うパスワード（明らかにテスト用と分かる値）。 */
    public static final String PASSWORD = "テスト用パスワード-0000";

    private final UserAccountService userAccountService;

    private final AuthApi api;

    /**
     * 作る。
     *
     * @param userAccountService 利用者の作成
     * @param port アプリの待ち受けの番号
     */
    public AdminTestUsers(UserAccountService userAccountService, int port) {
        this.userAccountService = userAccountService;
        this.api = new AuthApi(port);
    }

    /** テストで作った利用者。 */
    public record TestUser(long userId, String email, boolean admin) {}

    /**
     * 管理者を作る。
     *
     * @return 作った利用者
     */
    public TestUser createAdmin() {
        return create(true);
    }

    /**
     * 管理者でない利用者を作る。
     *
     * @return 作った利用者
     */
    public TestUser createNonAdmin() {
        return create(false);
    }

    /**
     * ログインしてアクセストークンを得る。
     *
     * @param user 利用者
     * @return アクセストークン
     */
    public String accessToken(TestUser user) {
        return AuthApi.accessToken(api.login(user.email(), PASSWORD));
    }

    private TestUser create(boolean admin) {
        String email = (admin ? "admin-" : "member-") + UUID.randomUUID() + "@example.com";
        long userId = userAccountService
                .createUser(email, new Password(PASSWORD), admin)
                .userId();
        return new TestUser(userId, email, admin);
    }
}
