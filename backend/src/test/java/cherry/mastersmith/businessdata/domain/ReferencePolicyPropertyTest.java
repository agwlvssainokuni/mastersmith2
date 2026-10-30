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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.kind;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * 他テーブル参照の判定と組み立ての関数の性質ベースのテスト（jqwik。BR3.1・BR3.2・BR3.5・BR4.4・BR7.6、NFR1.14、team.md の
 * 「性質ベースのテスト」）。失敗のときの乱数の種は、既存のテストの出力の設定（{@code exceptionFormat = FULL}）で失敗の報告に残り、
 * {@code @Property(seed = "...")} に与えて再現する（{@code junit-platform.properties} の jqwik の記録の置き場と同じ扱い）。
 */
class ReferencePolicyPropertyTest {

    /** 参照先のカラムの名前（c0 は主キー）。 */
    private static final List<String> COLUMNS = List.of("c0", "c1", "c2", "c3", "c4", "c5");

    private static final List<MainPermission> MAINS =
            List.of(MainPermission.NONE, MainPermission.READ, MainPermission.FULL);

    private static final DslTable TARGET = table(
            "target",
            false,
            List.of("c0"),
            COLUMNS.stream().map(name -> column(name).build()).toArray(DslColumn[]::new));

    /** 型（c1 は整数、c2 はバイナリ、ほかは文字）。 */
    private static final Map<String, ColumnKind> KINDS =
            Map.of("c1", kind(ValueType.INTEGER), "c2", kind(ValueType.BINARY));

    private static TableAccessView view(MainPermission table, List<Integer> mains) {
        Map<String, MainPermission> columnMain = new HashMap<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            columnMain.put(COLUMNS.get(i), MAINS.get(mains.get(i)));
        }
        return TableAccessPolicy.evaluate(
                BusinessDataDslFixture.input(TARGET, table, columnMain, KINDS, false, false, false));
    }

    private static ReferenceTarget lookup(int value, int label, List<Integer> search, List<Integer> list) {
        List<LookupSearchItem> searchItems = new ArrayList<>();
        SearchOperator[] operators = SearchOperator.values();
        for (int i = 0; i < search.size(); i++) {
            searchItems.add(new LookupSearchItem(
                    COLUMNS.get(search.get(i) % COLUMNS.size()), operators[search.get(i) % operators.length]));
        }
        List<LookupListItem> listItems = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            listItems.add(new LookupListItem(COLUMNS.get(list.get(i)), (i % 3) + 1));
        }
        return ReferenceTarget.from(
                        "sample",
                        new OptionSource(
                                OptionSourceKind.LOOKUP,
                                List.of(),
                                "target",
                                COLUMNS.get(value),
                                COLUMNS.get(label),
                                searchItems,
                                listItems))
                .orElseThrow();
    }

    @Provide
    Arbitrary<List<Integer>> mains() {
        return Arbitraries.integers().between(0, 2).list().ofSize(COLUMNS.size());
    }

    @Provide
    Arbitrary<List<Integer>> columnIndexes() {
        return Arbitraries.integers()
                .between(0, COLUMNS.size() - 1)
                .list()
                .ofMinSize(1)
                .ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<Integer>> searchCodes() {
        return Arbitraries.integers().between(0, 29).list().ofMinSize(1).ofMaxSize(4);
    }

    @Property
    @Label("adding permissions never turns AVAILABLE into NO_PERMISSION")
    void morePermissionKeepsAvailable(
            @ForAll @IntRange(min = 0, max = 5) int value,
            @ForAll @IntRange(min = 0, max = 5) int label,
            @ForAll("searchCodes") List<Integer> search,
            @ForAll("columnIndexes") List<Integer> list,
            @ForAll("mains") List<Integer> mains,
            @ForAll("mains") List<Integer> extra,
            @ForAll @IntRange(min = 1, max = 2) int tableMain,
            @ForAll boolean reference) {
        ReferenceTarget target = reference
                ? ReferenceTarget.from(
                                "sample",
                                new OptionSource(
                                        OptionSourceKind.REFERENCE,
                                        List.of(),
                                        "target",
                                        COLUMNS.get(value),
                                        COLUMNS.get(label),
                                        List.of(),
                                        List.of()))
                        .orElseThrow()
                : lookup(value, label, search, list);
        List<Integer> more = new ArrayList<>();
        for (int i = 0; i < mains.size(); i++) {
            more.add(Math.max(mains.get(i), extra.get(i)));
        }

        ReferenceStatus before = ReferencePolicy.accessOf(target, Optional.of(view(MAINS.get(tableMain), mains)));
        ReferenceStatus after = ReferencePolicy.accessOf(target, Optional.of(view(MainPermission.FULL, more)));

        if (before == ReferenceStatus.AVAILABLE) {
            assertThat(after).isEqualTo(ReferenceStatus.AVAILABLE);
        }
    }

    @Property
    @Label("NONE columns never appear in the picker's search fields or list columns")
    void noneColumnsNeverAppear(
            @ForAll @IntRange(min = 0, max = 5) int label,
            @ForAll("searchCodes") List<Integer> search,
            @ForAll("columnIndexes") List<Integer> list,
            @ForAll("mains") List<Integer> mains) {
        ReferenceTarget target = lookup(0, label, search, list);
        TableAccessView view = view(MainPermission.READ, mains);
        Set<String> none = new java.util.HashSet<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            if (mains.get(i) == 0) {
                none.add(COLUMNS.get(i));
            }
        }

        List<PickerSearchField> fields = ReferencePolicy.usableSearchFields(target, view);
        assertThat(fields).allSatisfy(f -> assertThat(none).doesNotContain(f.column()));
        assertThat(fields)
                .allSatisfy(f -> assertThat(f.operator()).isIn(SearchMode.EQUALS, SearchMode.CONTAINS))
                .allSatisfy(f -> assertThat(f.valueType()).isNotEqualTo(ValueType.BINARY));
        if (ReferencePolicy.accessOf(target, Optional.of(view)) == ReferenceStatus.AVAILABLE) {
            PickerInfo picker = ReferencePolicy.pickerOf(target, view);
            assertThat(picker.listColumns()).allSatisfy(c -> assertThat(none).doesNotContain(c.column()));
        }
    }

    @Property
    @Label("distinct by value leaves no duplicates and keeps the order of first appearances")
    void distinctKeepsFirstAppearances(@ForAll @Size(max = 60) List<@IntRange(min = 0, max = 9) Integer> values) {
        List<BoundValue> rows = values.stream()
                .map(v -> new BoundValue(ValueType.DECIMAL, new BigDecimal(v).setScale(v % 3)))
                .toList();

        List<BoundValue> kept = ReferencePolicy.distinctByValue(rows, r -> r);

        List<Integer> firsts = values.stream().distinct().toList();
        assertThat(kept)
                .extracting(b -> ((BigDecimal) b.value()).intValueExact())
                .containsExactlyElementsOf(firsts);
        for (int i = 0; i < kept.size(); i++) {
            for (int j = i + 1; j < kept.size(); j++) {
                assertThat(ReferencePolicy.sameReferenceValue(kept.get(i), kept.get(j)))
                        .isFalse();
            }
        }
    }

    @Property
    @Label("same reference value is reflexive and symmetric for text")
    void reflexiveAndSymmetric(@ForAll String left, @ForAll String right) {
        BoundValue a = new BoundValue(ValueType.STRING, left);
        BoundValue b = new BoundValue(ValueType.STRING, right);

        assertThat(ReferencePolicy.sameReferenceValue(a, new BoundValue(ValueType.STRING, new String(left))))
                .isTrue();
        assertThat(ReferencePolicy.sameReferenceValue(a, b)).isEqualTo(ReferencePolicy.sameReferenceValue(b, a));
        assertThat(ReferencePolicy.sameReferenceValue(a, b)).isEqualTo(left.equals(right));
    }

    @Property
    @Label("a difference in letter case or trailing spaces never matches")
    void caseAndTrailingSpacesDoNotMatch(
            @ForAll("letters") String text, @ForAll @IntRange(min = 1, max = 3) int spaces) {
        BoundValue base = new BoundValue(ValueType.STRING, text);

        assertThat(ReferencePolicy.sameReferenceValue(
                        base, new BoundValue(ValueType.STRING, text + " ".repeat(spaces))))
                .isFalse();
        String swapped = text.chars()
                .mapToObj(c -> Character.isUpperCase(c)
                        ? String.valueOf(Character.toLowerCase((char) c))
                        : String.valueOf(Character.toUpperCase((char) c)))
                .collect(Collectors.joining());
        assertThat(ReferencePolicy.sameReferenceValue(base, new BoundValue(ValueType.STRING, swapped)))
                .isFalse();
    }

    @Provide
    Arbitrary<String> letters() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Property
    @Label("trailing zeros of a decimal do match, so 1.0 and 1 are the same value")
    void trailingZerosMatch(
            @ForAll @IntRange(min = -100000, max = 100000) int unscaled,
            @ForAll @IntRange(min = 0, max = 4) int scale,
            @ForAll @IntRange(min = 1, max = 5) int zeros) {
        BigDecimal value = BigDecimal.valueOf(unscaled, scale);

        assertThat(ReferencePolicy.sameReferenceValue(
                        new BoundValue(ValueType.DECIMAL, value),
                        new BoundValue(ValueType.DECIMAL, value.setScale(scale + zeros))))
                .isTrue();
    }
}
