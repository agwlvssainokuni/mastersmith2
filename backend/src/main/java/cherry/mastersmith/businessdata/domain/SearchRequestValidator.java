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

import cherry.mastersmith.dsl.domain.SortDirection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一覧と検索・詳細の本文を確かめる純粋な関数（BR1.6・BR4.1・BR4.2・BR4.4・BR4.5・BR4.7・BR4.10・BR5.1・BR5.2・BR6.2、NFR4.5、
 * NFR 設計の Q4: A・Q6: A）。誤りはまとめて返す。型として読む処理は {@link ColumnValueReader} の口で受ける。
 *
 * <p>NONE のカラムと DSL に無いカラムは、{@link TableAccessView} に無いため同じ答え（{@code NOT_ALLOWED}）になる。要求の名前が
 * {@link TableAccessView} に無いときは、その名前を誤りの {@code field} に入れず空の文字にする（長い名前や引用符などを応答とログに
 * 載せないため。Intent 261025-deps-perf-carryover の FR3.7 R-07）。
 */
public final class SearchRequestValidator {

    /** 検索の値と主キーの値の長さの上限（コードポイント。NFR4.5）。 */
    public static final int MAX_VALUE_LENGTH = 1_000;

    private SearchRequestValidator() {}

    /**
     * 一覧と検索の本文を確かめる。
     *
     * @param view 判定の材料
     * @param input 本文
     * @param reader 型として読む口
     * @return 確かめの結果
     */
    public static SearchValidation validateSearch(TableAccessView view, SearchInput input, ColumnValueReader reader) {
        Objects.requireNonNull(view, "view は必須です");
        Objects.requireNonNull(input, "input は必須です");
        Objects.requireNonNull(reader, "reader は必須です");
        List<BusinessDataFieldError> errors = new ArrayList<>();
        List<SearchCondition> conditions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RawCondition raw : input.conditions()) {
            if (raw == null || raw.column() == null) {
                errors.add(new BusinessDataFieldError("", BusinessDataFieldErrorReason.NOT_ALLOWED));
                continue;
            }
            Optional<ColumnAccess> column = view.column(raw.column()).filter(c -> c.search() != null);
            if (column.isEmpty() || !seen.add(raw.column())) {
                errors.add(new BusinessDataFieldError(
                        known(view, raw.column()), BusinessDataFieldErrorReason.NOT_ALLOWED));
                continue;
            }
            condition(column.get(), raw, reader, errors).ifPresent(conditions::add);
        }
        SortKey sort = sort(view, input.sort(), errors);
        Long page = page(input.page());
        Integer size = size(input.size());
        if (!errors.isEmpty() || page == null || size == null) {
            return new SearchValidation.Invalid(errors);
        }
        return new SearchValidation.Valid(conditions, new PageRequest(page, size, sort));
    }

    /**
     * 詳細の本文の主キーを確かめる（BR6.2）。DETAIL が許されるテーブルだけで呼ぶ。
     *
     * @param view 判定の材料
     * @param input 本文の主キー
     * @param reader 型として読む口
     * @return 確かめの結果
     */
    public static KeyValidation validateKey(TableAccessView view, KeyInput input, ColumnValueReader reader) {
        Objects.requireNonNull(view, "view は必須です");
        Objects.requireNonNull(input, "input は必須です");
        Objects.requireNonNull(reader, "reader は必須です");
        if (!view.allows(AllowedOperation.DETAIL)) {
            throw new IllegalStateException("DETAIL が許されないテーブルでは主キーを確かめません");
        }
        List<BusinessDataFieldError> errors = new ArrayList<>();
        Set<String> keyNames = new LinkedHashSet<>(view.primaryKey());
        for (String name : keyNames) {
            if (!input.values().containsKey(name)) {
                errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.KEY_MISMATCH));
            }
        }
        for (String name : input.values().keySet()) {
            if (!keyNames.contains(name)) {
                errors.add(new BusinessDataFieldError(known(view, name), BusinessDataFieldErrorReason.KEY_MISMATCH));
            }
        }
        Map<String, BoundValue> values = new LinkedHashMap<>();
        for (ColumnAccess column : view.keyColumns()) {
            if (!input.values().containsKey(column.column())) {
                continue;
            }
            String text = input.values().get(column.column());
            Optional<BoundValue> value = text == null || tooLong(text) ? Optional.empty() : reader.read(column, text);
            if (value.isEmpty()) {
                errors.add(new BusinessDataFieldError(column.column(), BusinessDataFieldErrorReason.INVALID_VALUE));
            } else {
                values.put(column.column(), value.get());
            }
        }
        if (!errors.isEmpty()) {
            return new KeyValidation.Invalid(errors);
        }
        return new KeyValidation.Valid(new RowKey(values));
    }

    /**
     * 誤りの {@code field} に入れる名前を返す。このテーブルで読めるカラム（{@link TableAccessView} にあるもの）の名前だけをそのまま
     * 返し、DSL に無い名前・NONE のカラムの名前・null は空の文字にする（R-07。NONE と DSL に無いカラムは同じ答えのまま）。
     */
    private static String known(TableAccessView view, String name) {
        return name != null && view.column(name).isPresent() ? name : "";
    }

    /** 条件1つを確かめる。値が空なら条件にしない（BR4.2・BR4.4・BR4.5）。 */
    private static Optional<SearchCondition> condition(
            ColumnAccess column, RawCondition raw, ColumnValueReader reader, List<BusinessDataFieldError> errors) {
        String name = column.column();
        SearchRule rule = column.search();
        return switch (rule.mode()) {
            case EQUALS, CONTAINS -> {
                if (isEmpty(raw.value())) {
                    yield Optional.empty();
                }
                Optional<BoundValue> value = read(column, raw.value(), reader);
                if (value.isEmpty()) {
                    errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.INVALID_VALUE));
                    yield Optional.empty();
                }
                yield Optional.of(
                        rule.mode() == SearchMode.EQUALS
                                ? SearchCondition.equalTo(name, value.get())
                                : SearchCondition.contains(name, value.get()));
            }
            case RANGE -> range(column, raw, reader, errors);
            case CHOICE -> choice(column, raw, reader, errors);
        };
    }

    private static Optional<SearchCondition> range(
            ColumnAccess column, RawCondition raw, ColumnValueReader reader, List<BusinessDataFieldError> errors) {
        String name = column.column();
        boolean hasFrom = !isEmpty(raw.from());
        boolean hasTo = !isEmpty(raw.to());
        if (!hasFrom && !hasTo) {
            return Optional.empty();
        }
        Optional<BoundValue> from = hasFrom ? read(column, raw.from(), reader) : Optional.empty();
        Optional<BoundValue> to = hasTo ? read(column, raw.to(), reader) : Optional.empty();
        if ((hasFrom && from.isEmpty()) || (hasTo && to.isEmpty())) {
            errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.INVALID_VALUE));
            return Optional.empty();
        }
        if (from.isPresent() && to.isPresent() && compare(from.get(), to.get()) > 0) {
            errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.RANGE_REVERSED));
            return Optional.empty();
        }
        return Optional.of(SearchCondition.range(name, column.valueType(), from.orElse(null), to.orElse(null)));
    }

    private static Optional<SearchCondition> choice(
            ColumnAccess column, RawCondition raw, ColumnValueReader reader, List<BusinessDataFieldError> errors) {
        String name = column.column();
        List<String> choices = column.search().choices();
        Set<String> selected = new LinkedHashSet<>();
        for (String value : raw.values()) {
            if (!isEmpty(value)) {
                selected.add(value);
            }
        }
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        if (selected.size() > choices.size() || !choices.containsAll(selected)) {
            errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.NOT_A_CHOICE));
            return Optional.empty();
        }
        List<BoundValue> values = new ArrayList<>();
        for (String value : selected) {
            Optional<BoundValue> read = read(column, value, reader);
            if (read.isEmpty()) {
                errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.INVALID_VALUE));
                return Optional.empty();
            }
            values.add(read.get());
        }
        return Optional.of(SearchCondition.choice(name, column.valueType(), values));
    }

    /** 並べ替えを確かめる（BR5.2）。 */
    private static SortKey sort(TableAccessView view, RawSort raw, List<BusinessDataFieldError> errors) {
        if (raw == null) {
            return null;
        }
        String name = raw.column() == null ? "" : raw.column();
        Optional<ColumnAccess> column = view.column(name).filter(ColumnAccess::sortable);
        if (column.isEmpty()) {
            errors.add(new BusinessDataFieldError(known(view, name), BusinessDataFieldErrorReason.NOT_ALLOWED));
            return null;
        }
        SortDirection direction;
        if (raw.direction() == null) {
            direction = SortDirection.ASC;
        } else if (SortDirection.ASC.name().equals(raw.direction())) {
            direction = SortDirection.ASC;
        } else if (SortDirection.DESC.name().equals(raw.direction())) {
            direction = SortDirection.DESC;
        } else {
            errors.add(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.INVALID_VALUE));
            return null;
        }
        return new SortKey(name, direction);
    }

    /** ページの番号を読む（無ければ 1。整数でない・範囲の外なら null。BR5.1、Q4: A）。 */
    private static Long page(BigDecimal raw) {
        if (raw == null) {
            return 1L;
        }
        BigDecimal value = integral(raw);
        if (value == null
                || value.compareTo(BigDecimal.ONE) < 0
                || value.compareTo(BigDecimal.valueOf(PageRequest.MAX_PAGE)) > 0) {
            return null;
        }
        return value.longValueExact();
    }

    /** 1ページの件数を読む（無ければ 20。20・50・100 以外なら null。BR5.1）。 */
    private static Integer size(BigDecimal raw) {
        if (raw == null) {
            return PageRequest.DEFAULT_SIZE;
        }
        BigDecimal value = integral(raw);
        if (value == null || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            return null;
        }
        int size = value.intValueExact();
        return PageRequest.SIZES.contains(size) ? size : null;
    }

    /** 整数の値なら小数の桁の無い形で返し、整数でなければ null を返す。 */
    private static BigDecimal integral(BigDecimal raw) {
        BigDecimal stripped = raw.stripTrailingZeros();
        if (stripped.scale() > 0) {
            return null;
        }
        return stripped.setScale(0);
    }

    private static Optional<BoundValue> read(ColumnAccess column, String text, ColumnValueReader reader) {
        if (tooLong(text)) {
            return Optional.empty();
        }
        return reader.read(column, text);
    }

    private static boolean tooLong(String text) {
        return text.length() > MAX_VALUE_LENGTH && text.codePointCount(0, text.length()) > MAX_VALUE_LENGTH;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** 同じ型の2つの値を比べる（RANGE の向き。型ごとの Java の値は {@link Comparable}）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(BoundValue from, BoundValue to) {
        return ((Comparable) from.value()).compareTo(to.value());
    }
}
