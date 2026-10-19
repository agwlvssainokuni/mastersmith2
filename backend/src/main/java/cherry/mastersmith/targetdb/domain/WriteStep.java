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
package cherry.mastersmith.targetdb.domain;

import java.util.Objects;
import java.util.function.Function;

/**
 * 書き込みの口に渡した処理の答え（Intent 261016-business-data-views の U2、BR9.3、logical-components.md の L8）。口はこの答えに従って、
 * 確定・巻き戻し・セーブポイントへ戻して確かめる のどれかをしてから接続を返す。処理は例外を投げずに、この答えで返す。
 *
 * <p>文字列にするときは答えの種類だけを出す（結果の値を出さない）。
 *
 * @param <T> 処理の結果の型
 */
public sealed interface WriteStep<T> {

    /**
     * 確定する。
     *
     * @param result 処理の結果
     * @param <T> 処理の結果の型
     */
    record Commit<T>(T result) implements WriteStep<T> {

        /** 答えの種類だけを出す。 */
        @Override
        public String toString() {
            return "Commit";
        }
    }

    /**
     * 巻き戻す（件数が 1 でない・重なりの事前の問い合わせ・違反の確かめを要しない違反など）。巻き戻しが失敗しても、保存されていない
     * ことは確かなため、口はこの結果を返す。
     *
     * @param result 処理の結果
     * @param <T> 処理の結果の型
     */
    record Rollback<T>(T result) implements WriteStep<T> {

        /** 答えの種類だけを出す。 */
        @Override
        public String toString() {
            return "Rollback";
        }
    }

    /**
     * 書き込みの文の直前のセーブポイントへ戻し、同じ接続・同じトランザクションの中で確かめの処理（読み取りだけ）を行い、その後に全体を
     * 巻き戻す（一意・外部キーの違反のカラムを決めるため。BR6.2、reliability-design.md の 3節）。
     *
     * <p>セーブポイントへの戻し・確かめの処理の想定外の例外・最後の巻き戻しのどれかが失敗したら、口は {@code onFailure} の結果を返す
     * （BR6.4、計画の D4）。
     *
     * @param inspect 確かめの処理（例外を投げずに結果を返す）
     * @param onFailure 戻し・確かめ・最後の巻き戻しが失敗したときの結果
     * @param <T> 処理の結果の型
     */
    record RollbackToSavepointThenInspect<T>(
            Function<TargetWriteSession, T> inspect, Function<TargetWriteFailure, T> onFailure)
            implements WriteStep<T> {

        /** 必須の値を確かめる。 */
        public RollbackToSavepointThenInspect {
            Objects.requireNonNull(inspect, "inspect は必須です");
            Objects.requireNonNull(onFailure, "onFailure は必須です");
        }

        /** 答えの種類だけを出す。 */
        @Override
        public String toString() {
            return "RollbackToSavepointThenInspect";
        }
    }
}
