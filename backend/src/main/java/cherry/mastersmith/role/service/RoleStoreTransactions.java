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
package cherry.mastersmith.role.service;

import cherry.mastersmith.role.store.RoleStore;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * store を呼ぶ1つ目のトランザクションの入口（{@code logical-components.md} の L15、{@code reliability-design.md} 2.4、NFR3.4、計画の
 * D-4）。
 *
 * <p>結果が {@link RoleFirstStep.Done} 以外なら、必ず {@code setRollbackOnly()} を付けてから普通に戻る。Hibernate が違反の時点で
 * 巻き戻しの印を付けるため、付け忘れると確定のときに {@code UnexpectedRollbackException}（500）になる。巻き戻しの印をここに集め、
 * 操作ごとに付け忘れることを構造で防ぐ。{@link RoleStore} を持つのはこのクラスだけ（{@code RoleBoundaryArchitectureTest}）。
 *
 * <p>入れ子の REQUIRES_NEW は使わない。1つ目の接続を返してから2つ目を借りる。
 */
@Component
public class RoleStoreTransactions {

    private final RoleStore store;

    private final TransactionTemplate transaction;

    /**
     * 作る。
     *
     * @param store ロールの排他と書き込み
     * @param transactionManager トランザクションの管理
     */
    public RoleStoreTransactions(RoleStore store, PlatformTransactionManager transactionManager) {
        this.store = store;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 1つ目のトランザクションで store を使う処理を行う（排他 → 待ち合わせの口 → 業務の判定 → 書き込みと flush）。
     *
     * @param <T> 成功のときの値の型
     * @param work store を受けて結果を返す処理
     * @return 結果（{@link RoleFirstStep.Done} 以外なら巻き戻した後）
     */
    public <T> RoleFirstStep<T> inFirst(Function<RoleStore, RoleFirstStep<T>> work) {
        Objects.requireNonNull(work, "work");
        return Objects.requireNonNull(
                transaction.execute(status -> {
                    RoleFirstStep<T> step = Objects.requireNonNull(work.apply(store), "step");
                    if (!(step instanceof RoleFirstStep.Done<T>)) {
                        status.setRollbackOnly();
                    }
                    return step;
                }),
                "result");
    }
}
