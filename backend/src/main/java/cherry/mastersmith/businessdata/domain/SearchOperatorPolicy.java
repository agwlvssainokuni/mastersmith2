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

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchSetting;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 検索の演算子と型の表（BR4.6・BR4.9・BR4.10）の純粋な関数。DB の種類によらない（BR7.9 の型は {@link ColumnKind#equalsSupported()}
 * で受ける）。
 *
 * <p>手順: (1) 検索の設定が無い・password・BINARY は外す。(2) DSL の {@code IN} を {@code CHOICE} に読み替え、REFERENCE・LOOKUP の
 * 選択肢を持つカラムと、選択肢が無く真偽値でないカラムの {@code CHOICE} を {@code EQUALS} に読み替える（BR4.6）。(3) 読み替えた後の
 * 演算子と型の組を表に当て、表に無い組は外す（BR4.9・BR4.10）。
 */
public final class SearchOperatorPolicy {

    /** 真偽値の選択肢（選択肢が無い BOOLEAN の CHOICE。BR4.5）。表示名は値のまま（書式は画面が当てる）。 */
    public static final List<OptionItem> BOOLEAN_CHOICES = List.of(
            new OptionItem("true", new DisplayName("true", "true")),
            new OptionItem("false", new DisplayName("false", "false")));

    private SearchOperatorPolicy() {}

    /**
     * カラムの検索の決まりを求める。
     *
     * @param search DSL の検索の設定
     * @param options DSL の選択肢の出どころ（無ければ null）
     * @param kind カラムの受け渡しの型
     * @param masked フォーム部品が password なら true
     * @return 検索の決まり（検索の項目に出さないなら空）
     */
    public static Optional<SearchRule> rule(
            SearchSetting search, OptionSource options, ColumnKind kind, boolean masked) {
        if (search == null || !search.enabled() || search.operator() == null) {
            return Optional.empty();
        }
        ValueType type = kind.valueType();
        if (masked || type == ValueType.BINARY) {
            return Optional.empty();
        }
        SearchMode mode =
                switch (search.operator()) {
                    case EQUALS -> SearchMode.EQUALS;
                    case CONTAINS -> SearchMode.CONTAINS;
                    case RANGE -> SearchMode.RANGE;
                    case CHOICE, IN -> SearchMode.CHOICE;
                };
        List<OptionItem> choices = List.of();
        if (mode == SearchMode.CHOICE) {
            if (options != null && options.source() == OptionSourceKind.FIXED) {
                Set<String> seen = new HashSet<>();
                choices = options.items().stream()
                        .filter(item -> seen.add(item.value()))
                        .toList();
            } else if (options == null && type == ValueType.BOOLEAN) {
                choices = BOOLEAN_CHOICES;
            } else {
                mode = SearchMode.EQUALS;
            }
        }
        if (!allowed(mode, type, kind.equalsSupported())) {
            return Optional.empty();
        }
        return Optional.of(new SearchRule(mode, choices));
    }

    /**
     * 読み替えた後の演算子と型の組が表にあるかを返す（BR4.9）。password・BINARY は呼ぶ前に外している。
     *
     * @param mode 演算子
     * @param type 受け渡しの型
     * @param equalsSupported 完全一致に使える型なら true（BR7.9）
     * @return 表にある組なら true
     */
    public static boolean allowed(SearchMode mode, ValueType type, boolean equalsSupported) {
        if (type == ValueType.BINARY) {
            return false;
        }
        return switch (mode) {
            case EQUALS, CHOICE -> type != ValueType.OTHER || equalsSupported;
            case CONTAINS -> type == ValueType.STRING;
            case RANGE -> type.rangeComparable();
        };
    }
}
