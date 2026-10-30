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

import java.util.List;
import java.util.Objects;

/**
 * 既存の行の SQL の部品の {@code search} に渡す、参照先の読み取りの指定（Intent 261029-reference-lookup-cleanup の U1、ADR-002、
 * BR11.2・BR11.4、NFR 設計 performance-design.md 1.1、entities.md の ReferenceReadSpec）。指定が無いときは今の一覧と同じ動き
 * （BR12.4）。新しい SQL のメソッドは作らず、文ごとの時間の上限は持たない（渡す session の上限で絞る。BR8.4）。
 *
 * <p>名前（カラムの名前）だけを持ち、値を持たない。
 *
 * @param valueColumn 値のカラム（{@code SUBSTRING(…,1,10001)} で選ぶ。文字の型なら長さの条件を足す）
 * @param displayColumns ラベルのカラムと LOOKUP の一覧の列（一覧の上限で切って選ぶ。バイナリ・password は入れない。BR7.4）
 * @param excludeNullValue 値のカラムが NULL の行を読まない条件を足すなら true（BR4.2・BR5.8）
 * @param order 固定の並び（{@link ReferencePolicy#fixedOrder} で作る。利用者の並べ替えからは作らない）
 * @param count 件数の文を流すなら true（参照先の読み取りでは常に偽。BR5.7）
 * @param maxRows 読む行の上限（{@code LIMIT} と {@code setMaxRows}。BR8.6）
 * @param offset 読み飛ばす行の数（ピッカーの検索だけ。ほかは 0）
 * @param maxValueLength 値のカラムが文字の型のときの長さ（文字の数）の上限（{@code CHAR_LENGTH(値) <= この値}。NFR2.8）
 */
public record ReferenceReadSpec(
        String valueColumn,
        List<String> displayColumns,
        boolean excludeNullValue,
        List<SortKey> order,
        boolean count,
        int maxRows,
        int offset,
        int maxValueLength) {

    /** 値の長さの上限（{@code ColumnWriteRulesFactory.MAX_VALUE_LENGTH} と同じ。NFR2.8・Q1: A）。 */
    public static final int MAX_VALUE_LENGTH = ColumnWriteRulesFactory.MAX_VALUE_LENGTH;

    /** REFERENCE の選択肢・{@code countState} で読む行の上限（200 を超えたことを知るため 201。BR4.3・BR8.6）。 */
    public static final int OPTION_ROWS = ReferenceField.MAX_OPTIONS + 1;

    /** 必須の値と、行の数・読み飛ばす数・長さの上限の範囲を確かめる。 */
    public ReferenceReadSpec {
        Objects.requireNonNull(valueColumn, "valueColumn は必須です");
        displayColumns = List.copyOf(displayColumns);
        order = List.copyOf(order);
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows は 1 以上です");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset は 0 以上です");
        }
        if (maxValueLength < 1) {
            throw new IllegalArgumentException("maxValueLength は 1 以上です");
        }
    }

    /**
     * REFERENCE の選択肢を読む指定を作る（値が NULL の行を除く・件数の文を流さない・201 行・読み飛ばさない。BR4.2）。
     *
     * @param valueColumn 値のカラム
     * @param displayColumns ラベルのカラム（バイナリ・password なら空）
     * @param order 固定の並び
     * @return 指定
     */
    public static ReferenceReadSpec options(String valueColumn, List<String> displayColumns, List<SortKey> order) {
        return new ReferenceReadSpec(valueColumn, displayColumns, true, order, false, OPTION_ROWS, 0, MAX_VALUE_LENGTH);
    }

    /** ピッカーの検索で読む行の上限（1ページ 20 件と、次の有無を知るための 21 行目。BR5.7・BR8.6）。 */
    public static final int PICKER_ROWS = ReferenceRowsQuery.PAGE_SIZE + 1;

    /** ラベルの1件読みで読む行の上限（BR6.3・BR8.6）。 */
    public static final int LABEL_ROWS = 1;

    /** 保存前の確かめ（{@code check}）で比べる行の数（BR9.4）。 */
    public static final int CHECK_COMPARED_ROWS = ReferenceField.MAX_OPTIONS;

    /** 保存前の確かめ（{@code check}）で読む行の上限（比べる 200 行と、まだあるかを知るだけの 1 行。BR8.6・BR9.4）。 */
    public static final int CHECK_ROWS = CHECK_COMPARED_ROWS + 1;

    /**
     * ピッカーの検索の指定を作る（値が NULL の行を除く・件数の文を流さない・21 行・{@code (page-1)×20} を読み飛ばす。BR5.7・BR5.8）。
     *
     * @param valueColumn 値のカラム
     * @param displayColumns ラベルのカラムと一覧の列（バイナリ・password は入れない）
     * @param order 固定の並び
     * @param offset 読み飛ばす行の数
     * @return 指定
     */
    public static ReferenceReadSpec picker(
            String valueColumn, List<String> displayColumns, List<SortKey> order, int offset) {
        return new ReferenceReadSpec(
                valueColumn, displayColumns, true, order, false, PICKER_ROWS, offset, MAX_VALUE_LENGTH);
    }

    /**
     * ラベルの1件読みの指定を作る（値の一致の条件は呼ぶ側が渡す。1行・読み飛ばさない。BR6.3）。
     *
     * @param valueColumn 値のカラム
     * @param displayColumns ラベルのカラム（バイナリ・password なら空）
     * @param order 固定の並び
     * @return 指定
     */
    public static ReferenceReadSpec label(String valueColumn, List<String> displayColumns, List<SortKey> order) {
        return new ReferenceReadSpec(valueColumn, displayColumns, true, order, false, LABEL_ROWS, 0, MAX_VALUE_LENGTH);
    }

    /**
     * 保存前の確かめの指定を作る（出すのは値のカラムだけ・201 行・読み飛ばさない。値の一致の条件は呼ぶ側が渡す。BR9.4）。
     *
     * @param valueColumn 値のカラム
     * @param order 固定の並び
     * @return 指定
     */
    public static ReferenceReadSpec check(String valueColumn, List<SortKey> order) {
        return new ReferenceReadSpec(valueColumn, List.of(), true, order, false, CHECK_ROWS, 0, MAX_VALUE_LENGTH);
    }

    /** カラムの名前と数だけを出す（値を持たない）。 */
    @Override
    public String toString() {
        return "ReferenceReadSpec[valueColumn=" + valueColumn + ", displayColumns=" + displayColumns + ", order="
                + order.size() + ", count=" + count + ", maxRows=" + maxRows + ", offset=" + offset + "]";
    }
}
