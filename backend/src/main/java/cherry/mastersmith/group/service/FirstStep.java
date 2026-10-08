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

import cherry.mastersmith.group.domain.GroupAuditDetail;
import cherry.mastersmith.group.domain.GroupRejection;
import cherry.mastersmith.group.store.StoreOutcome;
import java.util.Objects;

/**
 * store を呼ぶ1つ目のトランザクションの結果（計画の D-5、NFR 設計の読み直しの R-10）。
 *
 * <p>{@link Done} 以外は、{@link GroupStoreTransactions} が必ず巻き戻しの印を付ける（捨ての試しの T5）。書き込みの前の業務の拒否
 * （{@link Rejected}）も、違反・上限切れ（{@link Store}）も、1つ目を巻き戻した後に、失敗の出来事を2つ目のトランザクション（書き込み
 * なし）で出す（上限切れは出さない。BR5.7・BR8.3）。
 *
 * @param <T> 成功のときの値の型
 */
public sealed interface FirstStep<T> permits FirstStep.Done, FirstStep.Rejected, FirstStep.Store {

    /**
     * 成功した（成功の出来事は1つ目のトランザクションの中で出し済み）。
     *
     * @param <T> 値の型
     * @param value 値（値の無い操作では null）
     */
    record Done<T>(T value) implements FirstStep<T> {}

    /**
     * 書き込みの前に業務の判定で拒否した。
     *
     * @param <T> 値の型
     * @param rejection 拒否の理由
     * @param detail 失敗の出来事の detail（グループ・利用者が無い拒否では null）
     */
    record Rejected<T>(GroupRejection rejection, GroupAuditDetail detail) implements FirstStep<T> {

        /** 理由が null でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /**
     * 排他か書き込みが成功しなかった（行が無い以外の、違反・上限切れ）。
     *
     * @param <T> 値の型
     * @param outcome store の結果（{@link StoreOutcome.Done} 以外）
     * @param detail 違反を業務の拒否に読み替えたときの失敗の出来事の detail（無ければ null）
     */
    record Store<T>(StoreOutcome<?> outcome, GroupAuditDetail detail) implements FirstStep<T> {

        /** 結果が成功でないことを確かめる。 */
        public Store {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome.isDone()) {
                throw new IllegalArgumentException("成功の結果は Done で返します");
            }
        }
    }
}
