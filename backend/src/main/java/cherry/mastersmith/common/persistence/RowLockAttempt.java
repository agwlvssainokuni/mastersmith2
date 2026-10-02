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
package cherry.mastersmith.common.persistence;

/**
 * 行の排他の口の結果（{@code reliability-design.md} 5.2）。排他を取れた（{@link Acquired}）か、待ちの上限切れ・行き詰まりで取れ
 * なかった（{@link Busy}）か。取れなかったときの元の例外は持たない（連なりの文に行の値が入りうるため）。
 *
 * @param <T> 取れたときの値の型
 */
public sealed interface RowLockAttempt<T> permits RowLockAttempt.Acquired, RowLockAttempt.Busy {

    /**
     * 排他を取れた結果を作る。
     *
     * @param <T> 値の型
     * @param value 排他の後に読んだ値
     * @return 取れた結果
     */
    static <T> RowLockAttempt<T> acquired(T value) {
        return new Acquired<>(value);
    }

    /**
     * 排他を取れなかった結果を作る。
     *
     * @param <T> 値の型
     * @return 取れなかった結果
     */
    static <T> RowLockAttempt<T> busy() {
        return new Busy<>();
    }

    /**
     * 排他を取れた。
     *
     * @param <T> 値の型
     * @param value 排他の後に読んだ値
     */
    record Acquired<T>(T value) implements RowLockAttempt<T> {}

    /**
     * 排他を取れなかった（待ちの上限切れ・行き詰まり）。
     *
     * @param <T> 値の型
     */
    record Busy<T>() implements RowLockAttempt<T> {}
}
