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
package cherry.mastersmith.auth.testsupport;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.service.RevokeAllResult;
import cherry.mastersmith.user.service.UserAccountService;
import java.util.Objects;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 結合テストで利用者を止める・解くテストの補助（契約 C1 の口だけを使う。Intent 260930-user-admin の U1）。
 *
 * <p>止めるときは、利用を止める操作（U3）と同じ組で、1つのトランザクションの中で停止の状態の書き換えとリフレッシュトークンの
 * まとめての無効化を行う。解くときは停止の状態の書き換えだけを行う（無効にしたトークンは戻らない）。テストの中で停止の状態を入れる口は
 * これだけにし、JDBC で {@code users} を直接書き換えない。
 */
public final class TestUserSuspension {

    private final TransactionTemplate transaction;

    private final UserAccountService userAccountService;

    private final RefreshTokenRevocationService revocationService;

    /**
     * 補助を作る。
     *
     * @param transaction トランザクションの雛形
     * @param userAccountService 利用者の業務処理
     * @param revocationService リフレッシュトークンのまとめての無効化
     */
    public TestUserSuspension(
            TransactionTemplate transaction,
            UserAccountService userAccountService,
            RefreshTokenRevocationService revocationService) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.userAccountService = Objects.requireNonNull(userAccountService, "userAccountService");
        this.revocationService = Objects.requireNonNull(revocationService, "revocationService");
    }

    /**
     * 利用者を止め、リフレッシュトークンをまとめて無効にする（同じトランザクション）。
     *
     * @param userId 利用者 ID
     * @return 無効にしたリフレッシュトークンの数
     */
    public int suspend(long userId) {
        RevokeAllResult result = transaction.execute(status -> {
            userAccountService.setSuspended(userId, true);
            return revocationService.revokeAllRefreshTokens(userId);
        });
        return Objects.requireNonNull(result, "result").revoked();
    }

    /**
     * 利用者の停止を解く（停止の状態の書き換えだけ）。
     *
     * @param userId 利用者 ID
     */
    public void resume(long userId) {
        transaction.executeWithoutResult(status -> userAccountService.setSuspended(userId, false));
    }
}
