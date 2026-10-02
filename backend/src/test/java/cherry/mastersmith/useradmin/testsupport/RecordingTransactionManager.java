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

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 巻き戻しの印（{@code setRollbackOnly()}）の回数と、確定・巻き戻しを記録する偽のトランザクションの管理（Intent 260930-user-admin の
 * U3、{@code reliability-design.md} 5.3 の単体テスト）。{@code TransactionTemplate} に渡して、本番の業務処理が Busy のときに印を付ける
 * ことを確かめる。
 *
 * <p>本物と同じく、印の付いた状態で確定を求められたら巻き戻しとして数える（例外は投げない）。DB には触れない。
 */
public final class RecordingTransactionManager implements PlatformTransactionManager {

    private final List<TransactionDefinition> definitions = new ArrayList<>();

    private int rollbackOnlyMarks;

    private int commits;

    private int rollbacks;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        definitions.add(definition);
        return new SimpleTransactionStatus() {
            @Override
            public void setRollbackOnly() {
                rollbackOnlyMarks++;
                super.setRollbackOnly();
            }
        };
    }

    @Override
    public void commit(TransactionStatus status) {
        if (status.isRollbackOnly()) {
            rollbacks++;
        } else {
            commits++;
        }
    }

    @Override
    public void rollback(TransactionStatus status) {
        rollbacks++;
    }

    /**
     * 巻き戻しの印が付けられた回数を返す。
     *
     * @return 回数
     */
    public int rollbackOnlyMarks() {
        return rollbackOnlyMarks;
    }

    /**
     * 確定した回数を返す。
     *
     * @return 回数
     */
    public int commits() {
        return commits;
    }

    /**
     * 巻き戻した回数（印の付いた確定を含む）を返す。
     *
     * @return 回数
     */
    public int rollbacks() {
        return rollbacks;
    }

    /**
     * 始めたトランザクションの定義を返す。
     *
     * @return 定義の一覧（始めた順）
     */
    public List<TransactionDefinition> definitions() {
        return List.copyOf(definitions);
    }
}
