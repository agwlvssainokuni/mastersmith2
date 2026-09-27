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
package cherry.mastersmith.user.testsupport;

import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.service.CreateUserResult;
import cherry.mastersmith.user.service.NewUser;
import cherry.mastersmith.user.service.UserAccountService;

/**
 * 結合テストで利用者を作るテストの補助（契約 C2 の {@code createUser(NewUser)} を、テストに要る値だけで呼ぶ）。
 *
 * <p>氏名はメールアドレスと違う値（{@link #DISPLAY_NAME}）にする（漏えいのテストで、氏名の出力とメールアドレスの出力を区別するため）。
 * 表示の設定は初期値（ja・system・md）。
 */
public final class TestUserAccounts {

    /** テストで作る利用者の氏名。 */
    public static final String DISPLAY_NAME = "テスト 利用者";

    private TestUserAccounts() {}

    /**
     * 利用者を作り、利用者 ID を返す。
     *
     * @param service 利用者の作成の業務処理
     * @param email メールアドレス
     * @param password パスワード（作成時の規則に合う値）
     * @param admin 管理者か
     * @return 作った利用者の ID
     * @throws IllegalStateException 同じメールアドレスの利用者がすでにいるとき
     */
    public static long create(UserAccountService service, String email, String password, boolean admin) {
        CreateUserResult result = service.createUser(new NewUser(
                email, DISPLAY_NAME, new Password(password), Language.JA, Theme.SYSTEM, FontSize.MD, admin));
        if (result instanceof CreateUserResult.Created created) {
            return created.userId();
        }
        throw new IllegalStateException("同じメールアドレスの利用者がすでにいます");
    }
}
