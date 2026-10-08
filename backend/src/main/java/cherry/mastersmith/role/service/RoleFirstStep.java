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

import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import java.util.Objects;

/**
 * store を呼ぶ1つ目のトランザクションの結果（計画の 4.3・D-4、13節 Q2: A。group の {@code FirstStep} と同じ形）。
 *
 * <p>{@link Done} 以外は、{@link RoleStoreTransactions} が必ず巻き戻しの印を付ける。書き込みの前の業務の拒否（{@link Rejected}）も、
 * 違反・上限切れ（{@link Store}）も、1つ目を巻き戻した後に、失敗の出来事を2つ目のトランザクション（書き込みなし）で出す（上限切れは
 * 出さない。BR8.3・BR8.5）。文字列にしたときは種類だけを出す（計画の D-5）。
 *
 * @param <T> 成功のときの値の型
 */
public sealed interface RoleFirstStep<T> permits RoleFirstStep.Done, RoleFirstStep.Rejected, RoleFirstStep.Store {

    /**
     * 成功した（成功の出来事は1つ目のトランザクションの中で出し済み）。
     *
     * @param <T> 値の型
     * @param value 値（値の無い操作では null）
     */
    record Done<T>(T value) implements RoleFirstStep<T> {

        /** 種類だけを出す。 */
        @Override
        public String toString() {
            return "Done";
        }
    }

    /**
     * 書き込みの前に業務の判定で拒否した。
     *
     * @param <T> 値の型
     * @param rejection 拒否の理由
     * @param detail 失敗の出来事の detail（ロールが無い拒否では null）
     */
    record Rejected<T>(RoleRejection rejection, RoleAuditDetail detail) implements RoleFirstStep<T> {

        /** 理由が null でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }

        /** 種類と理由だけを出す。 */
        @Override
        public String toString() {
            return "Rejected[" + rejection + "]";
        }
    }

    /**
     * 排他か書き込みが成功しなかった（行が無い以外の、違反・上限切れ）。
     *
     * @param <T> 値の型
     * @param outcome store の結果（{@link RoleStoreOutcome.Done} 以外）
     * @param detail 違反を業務の拒否に読み替えたときの失敗の出来事の detail（無ければ null）
     */
    record Store<T>(RoleStoreOutcome<?> outcome, RoleAuditDetail detail) implements RoleFirstStep<T> {

        /** 結果が成功でないことを確かめる。 */
        public Store {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome.isDone()) {
                throw new IllegalArgumentException("成功の結果は Done で返します");
            }
        }

        /** 種類と store の結果の種類だけを出す。 */
        @Override
        public String toString() {
            return "Store[" + outcome + "]";
        }
    }
}
