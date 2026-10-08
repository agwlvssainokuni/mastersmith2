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
package cherry.mastersmith.group.service;

import cherry.mastersmith.group.store.GroupStore;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * store を呼ぶ1つ目のトランザクションの入口（{@code logical-components.md} の L12、{@code reliability-design.md} 2.3、NFR3.4）。
 *
 * <p>結果が {@link FirstStep.Done} 以外なら、必ず {@code setRollbackOnly()} を付けてから普通に戻る。Hibernate が違反の時点で巻き戻しの
 * 印を付けるため、付け忘れると確定のときに {@code UnexpectedRollbackException}（500）になる（捨ての試しの T5）。巻き戻しの印をここに
 * 集め、操作ごとに付け忘れることを構造で防ぐ（読み直しの R-05。U4 の {@code RoleStoreTransactions} と同じ形）。
 *
 * <p>入れ子の REQUIRES_NEW は使わない。1つ目の接続を返してから2つ目を借りる。
 */
@Component
public class GroupStoreTransactions {

    private final GroupStore store;

    private final TransactionTemplate transaction;

    /**
     * 作る。
     *
     * @param store グループの排他と書き込み
     * @param transactionManager トランザクションの管理
     */
    public GroupStoreTransactions(GroupStore store, PlatformTransactionManager transactionManager) {
        this.store = store;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 1つ目のトランザクションで store を使う処理を行う（排他 → 待ち合わせの口 → 業務の判定 → 書き込みと flush）。
     *
     * @param <T> 成功のときの値の型
     * @param work store を受けて結果を返す処理
     * @return 結果（{@link FirstStep.Done} 以外なら巻き戻した後）
     */
    public <T> FirstStep<T> inFirst(Function<GroupStore, FirstStep<T>> work) {
        Objects.requireNonNull(work, "work");
        return Objects.requireNonNull(
                transaction.execute(status -> {
                    FirstStep<T> step = Objects.requireNonNull(work.apply(store), "step");
                    if (!(step instanceof FirstStep.Done<T>)) {
                        status.setRollbackOnly();
                    }
                    return step;
                }),
                "result");
    }
}
