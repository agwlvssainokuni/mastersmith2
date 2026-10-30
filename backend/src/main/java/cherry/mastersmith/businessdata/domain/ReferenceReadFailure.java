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
package cherry.mastersmith.businessdata.domain;

import java.util.Objects;

/**
 * 参照先の読み取りの失敗の値（Intent 261029-reference-lookup-cleanup の U1、NFR 設計 reliability-design.md 5.2。U2 との継ぎ目）。
 * {@code check}・{@code countState} の {@code Failed} と、定義の取得の失敗の WARN の1行の元になる。
 *
 * <p>例外のメッセージ・原因・行の値・識別子の名前を持たない（BR10.2）。全体の上限で流さずに止めたとき（例外が無い）は
 * {@link #ofBudgetExceeded()} で作る（設計の文書の名前 {@code budgetExceeded()} は項目の読み出しと重なり Java の型で作れないため、
 * 依頼者の決定でこの名前にした。U2 もこの名前を使う）。
 *
 * @param reason 理由
 * @param sqlState SQLState（無ければ null）
 * @param vendorCode 業者のエラーの番号（無ければ null）
 * @param exceptionClass 例外のクラスの名前（{@code budgetExceeded} が偽のとき必須、真のとき null）
 * @param budgetExceeded 全体の上限で流さずに止めたときだけ真（そのとき理由は {@link ReferenceFailureReason#TIMEOUT}）
 */
public record ReferenceReadFailure(
        ReferenceFailureReason reason,
        String sqlState,
        Integer vendorCode,
        String exceptionClass,
        boolean budgetExceeded) {

    /** 理由が必須で、全体の上限の止めと例外のクラスの名前の組が食い違わないことを確かめる。 */
    public ReferenceReadFailure {
        Objects.requireNonNull(reason, "reason は必須です");
        if (budgetExceeded) {
            if (reason != ReferenceFailureReason.TIMEOUT || exceptionClass != null) {
                throw new IllegalArgumentException("全体の上限の止めは TIMEOUT で、例外のクラスの名前を持ちません");
            }
        } else {
            Objects.requireNonNull(exceptionClass, "exceptionClass は必須です");
        }
    }

    /**
     * 行の SQL の失敗から作る（{@code MISMATCH}・{@code READ_CONVERSION} は {@code MISMATCH} に写す）。
     *
     * @param failure 行の SQL の失敗
     * @return 失敗の値
     * @throws IllegalArgumentException 理由が {@code INVALID_VALUE}・{@code UNEXPECTED} のとき（呼ぶ側は想定外の 500 として扱う）
     */
    public static ReferenceReadFailure of(RowQueryFailure failure) {
        Objects.requireNonNull(failure, "failure は必須です");
        ReferenceFailureReason reason =
                switch (failure.reason()) {
                    case TIMEOUT -> ReferenceFailureReason.TIMEOUT;
                    case UNAVAILABLE -> ReferenceFailureReason.UNAVAILABLE;
                    case MISMATCH, READ_CONVERSION -> ReferenceFailureReason.MISMATCH;
                    case INVALID_VALUE, UNEXPECTED ->
                        throw new IllegalArgumentException("参照先の読み取りの失敗に写さない理由です: " + failure.reason());
                };
        return new ReferenceReadFailure(
                reason, failure.sqlState(), failure.vendorCode(), failure.exceptionClass(), false);
    }

    /**
     * 全体の上限（残り 1 秒未満）で文を流さずに止めた失敗を作る（BR8.4）。
     *
     * @return 失敗の値（{@code TIMEOUT}・{@code budgetExceeded} が真）
     */
    public static ReferenceReadFailure ofBudgetExceeded() {
        return new ReferenceReadFailure(ReferenceFailureReason.TIMEOUT, null, null, null, true);
    }
}
