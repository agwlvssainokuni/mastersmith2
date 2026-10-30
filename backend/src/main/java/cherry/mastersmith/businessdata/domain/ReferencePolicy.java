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

import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 他テーブル参照の判定と組み立ての純粋な関数（Intent 261029-reference-lookup-cleanup の U1、BR3.1・BR3.2・BR3.5・BR4.4・BR5.1・
 * BR7.6・BR11.4、functional-spec.md 5節）。どれも写しから作った判定の材料（{@link TableAccessView}）と DSL から引いた参照先
 * （{@link ReferenceTarget}）だけを入力にし、対象DB を読まない。jqwik の性質ベースのテストの対象。
 */
public final class ReferencePolicy {

    private ReferencePolicy() {}

    /**
     * 参照先を読めるかを決める（BR3.1）。1つでも欠けたら {@link ReferenceStatus#NO_PERMISSION} とし、部分的に落として提供しない。
     *
     * <p>欠ける条件: 参照先のテーブルの判定の材料が無い（DSL に無い・主権限が NONE）／値のカラムが読めない（NONE・DSL に無い）／値の
     * カラムがバイナリか password／ラベルのカラムが読めない／LOOKUP で一覧の列のどれかが読めない／LOOKUP で使える検索の条件
     * （{@link #usableSearchFields}）が残らない。
     *
     * @param target 参照先
     * @param targetView 参照先のテーブルの判定の材料（参照元と同じ写しで作ったもの。DSL に無い・NONE なら空）
     * @return {@link ReferenceStatus#AVAILABLE} か {@link ReferenceStatus#NO_PERMISSION}
     */
    public static ReferenceStatus accessOf(ReferenceTarget target, Optional<TableAccessView> targetView) {
        Objects.requireNonNull(target, "target は必須です");
        Objects.requireNonNull(targetView, "targetView は必須です");
        if (targetView.isEmpty() || targetView.get().tableMain() == MainPermission.NONE) {
            return ReferenceStatus.NO_PERMISSION;
        }
        TableAccessView view = targetView.get();
        Optional<ColumnAccess> value = view.column(target.valueColumn());
        if (value.isEmpty() || !value.get().readsValue()) {
            return ReferenceStatus.NO_PERMISSION;
        }
        if (view.column(target.labelColumn()).isEmpty()) {
            return ReferenceStatus.NO_PERMISSION;
        }
        if (target.kind() == OptionSourceKind.LOOKUP) {
            for (LookupListItem item : target.lookupList()) {
                if (view.column(item.column()).isEmpty()) {
                    return ReferenceStatus.NO_PERMISSION;
                }
            }
            if (usableSearchFields(target, view).isEmpty()) {
                return ReferenceStatus.NO_PERMISSION;
            }
        }
        return ReferenceStatus.AVAILABLE;
    }

    /**
     * 使える検索の条件を選ぶ（BR3.2）。読めるカラムで、バイナリでも password でもなく、演算子が EQUALS か CONTAINS（文字の型だけ）の
     * 条件を DSL の並びで返す。RANGE・CHOICE・IN は使わない。
     *
     * @param target 参照先（REFERENCE なら空を返す）
     * @param view 参照先のテーブルの判定の材料
     * @return 使える検索の条件
     */
    public static List<PickerSearchField> usableSearchFields(ReferenceTarget target, TableAccessView view) {
        List<PickerSearchField> fields = new ArrayList<>();
        for (LookupSearchItem item : target.lookupSearch()) {
            Optional<ColumnAccess> column = view.column(item.column());
            if (column.isEmpty() || !column.get().readsValue()) {
                continue;
            }
            ColumnAccess c = column.get();
            SearchMode mode =
                    switch (item.operator()) {
                        case EQUALS -> SearchMode.EQUALS;
                        case CONTAINS -> c.valueType() == ValueType.STRING ? SearchMode.CONTAINS : null;
                        case RANGE, CHOICE, IN -> null;
                    };
            if (mode != null) {
                fields.add(new PickerSearchField(c.column(), c.displayName(), mode, c.valueType()));
            }
        }
        return List.copyOf(fields);
    }

    /**
     * ピッカーの情報を作る（BR5.1）。一覧の列は {@code order} の順で、判定の材料に無いカラム（NONE を含む）を必ず落とす（BR3.5 の
     * 守り）。参照先の名前は載せない（BR3.6）。
     *
     * @param target 参照先（LOOKUP）
     * @param view 参照先のテーブルの判定の材料（{@link #accessOf} が AVAILABLE のもの）
     * @return ピッカーの情報
     * @throws IllegalArgumentException 使える検索の条件か一覧の列が残らないとき（AVAILABLE でない参照先に呼んだ誤り）
     */
    public static PickerInfo pickerOf(ReferenceTarget target, TableAccessView view) {
        List<PickerListColumn> columns = new ArrayList<>();
        for (LookupListItem item : target.lookupList()) {
            view.column(item.column())
                    .ifPresent(c -> columns.add(new PickerListColumn(c.column(), c.displayName(), c.valueType())));
        }
        return new PickerInfo(usableSearchFields(target, view), columns);
    }

    /**
     * 固定の並びを作る（BR4.2・BR5.8・BR11.4）。ラベルの昇順 → 値の昇順 → 参照先の主キーの昇順（NULL を最後にするのは SQL の
     * 方言の側）。ラベルのカラムが値のカラムと同じなら重ねない。ラベルのカラムがバイナリか password のときは並びに使わない（値を
     * 読まないカラムで並べない）。利用者の並べ替えと既存の既定の並びからは作らない。
     *
     * @param target 参照先
     * @param view 参照先のテーブルの判定の材料
     * @return 並びのキー
     */
    public static List<SortKey> fixedOrder(ReferenceTarget target, TableAccessView view) {
        List<SortKey> keys = new ArrayList<>();
        view.column(target.labelColumn())
                .filter(ColumnAccess::readsValue)
                .ifPresent(c -> keys.add(new SortKey(c.column(), SortDirection.ASC)));
        addIfAbsent(keys, target.valueColumn());
        for (String key : view.primaryKey()) {
            addIfAbsent(keys, key);
        }
        return List.copyOf(keys);
    }

    /**
     * REFERENCE の選択肢を読む指定を作る（BR4.2・BR7.3・BR7.4・BR11.4）。表示の列はラベルのカラムだけで、バイナリ・password は
     * 選ばない。ラベルのカラムが値のカラムと同じときも、値は切らない選び方で、ラベルは一覧の上限で切る選び方で別に選ぶ（BR7.3）。
     *
     * @param target 参照先（REFERENCE）
     * @param view 参照先のテーブルの判定の材料
     * @return 指定
     */
    public static ReferenceReadSpec optionsSpec(ReferenceTarget target, TableAccessView view) {
        List<String> display = view.column(target.labelColumn())
                .filter(ColumnAccess::readsValue)
                .map(c -> List.of(c.column()))
                .orElse(List.of());
        return ReferenceReadSpec.options(target.valueColumn(), display, fixedOrder(target, view));
    }

    /**
     * 同じ値の2行目以降を落とす（BR4.4）。並びの順に見て、すでに出した値と {@link #sameReferenceValue} で同じ値の行を落とし、
     * 最初の出現の順を保つ。型として読めない値（null）は何とも一致しないため落とさない。
     *
     * @param rows 読んだ行（並びの順）
     * @param typedValue 行の値を型つきの値にする関数（読めなければ null）
     * @param <T> 行の型
     * @return 同じ値を落とした行
     */
    public static <T> List<T> distinctByValue(List<T> rows, Function<T, BoundValue> typedValue) {
        List<T> kept = new ArrayList<>();
        List<BoundValue> seen = new ArrayList<>();
        for (T row : rows) {
            BoundValue value = typedValue.apply(row);
            if (seen.stream().anyMatch(s -> sameReferenceValue(s, value))) {
                continue;
            }
            kept.add(row);
            seen.add(value);
        }
        return List.copyOf(kept);
    }

    /**
     * 参照の値の同じさを、型つきの値の厳密な一致で決める（BR7.6）。文字はコードポイントの完全な一致（大文字と小文字・末尾の空白を
     * 区別する）、小数と長い整数は数として比べ（1.0 と 1 は同じ）、時差つきの日時・時刻は同じ時点か、ほかは値の一致。型が違う値・
     * 読めない値（null）・NULL の値は一致しない。
     *
     * @param left 片方の値（読めなければ null）
     * @param right もう片方の値（読めなければ null）
     * @return 同じ値なら true
     */
    public static boolean sameReferenceValue(BoundValue left, BoundValue right) {
        if (left == null || right == null || left.value() == null || right.value() == null) {
            return false;
        }
        if (left.valueType() != right.valueType()) {
            return false;
        }
        Object a = left.value();
        Object b = right.value();
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        if (a instanceof OffsetDateTime x && b instanceof OffsetDateTime y) {
            return x.isEqual(y);
        }
        if (a instanceof OffsetTime x && b instanceof OffsetTime y) {
            return x.isEqual(y);
        }
        return a.equals(b);
    }

    private static void addIfAbsent(List<SortKey> keys, String column) {
        if (keys.stream().noneMatch(k -> k.column().equals(column))) {
            keys.add(new SortKey(column, SortDirection.ASC));
        }
    }
}
