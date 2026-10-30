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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 他テーブル参照の判定と組み立ての関数の単体テスト（BR2.5・BR3.1・BR3.2・BR3.5・BR4.2・BR4.4・BR7.6・BR11.4）。 */
class ReferencePolicyTest {

    /** 参照先のテーブル（id は主キーの整数、secret は password、blob はバイナリ、amount は小数）。 */
    private static final DslTable DEPT = table(
            "dept",
            false,
            List.of("id"),
            column("id").build(),
            column("name").build(),
            column("code").build(),
            column("secret").form(FormPart.PASSWORD).build(),
            column("blob").build(),
            column("amount").build());

    private static final Map<String, ColumnKind> KINDS = Map.of(
            "id", kind(ValueType.INTEGER),
            "blob", kind(ValueType.BINARY),
            "amount", kind(ValueType.DECIMAL));

    private static TableAccessView view(MainPermission table, Map<String, MainPermission> overrides) {
        Map<String, MainPermission> columnMain = new HashMap<>();
        DEPT.columns().keySet().forEach(name -> columnMain.put(name, MainPermission.READ));
        columnMain.putAll(overrides);
        return TableAccessPolicy.evaluate(
                BusinessDataDslFixture.input(DEPT, table, columnMain, KINDS, false, false, false));
    }

    private static Optional<TableAccessView> readable() {
        return Optional.of(view(MainPermission.READ, Map.of()));
    }

    private static ReferenceTarget reference(String valueColumn, String labelColumn) {
        return ReferenceTarget.from(
                        "sample",
                        new OptionSource(
                                OptionSourceKind.REFERENCE,
                                List.of(),
                                "dept",
                                valueColumn,
                                labelColumn,
                                List.of(),
                                List.of()))
                .orElseThrow();
    }

    private static ReferenceTarget lookup(
            String labelColumn, List<LookupSearchItem> search, List<LookupListItem> list) {
        return ReferenceTarget.from(
                        "sample",
                        new OptionSource(OptionSourceKind.LOOKUP, List.of(), "dept", "id", labelColumn, search, list))
                .orElseThrow();
    }

    private static ReferenceTarget standardLookup() {
        return lookup(
                null,
                List.of(
                        new LookupSearchItem("name", SearchOperator.CONTAINS),
                        new LookupSearchItem("code", SearchOperator.EQUALS)),
                List.of(new LookupListItem("code", 2), new LookupListItem("name", 1)));
    }

    @Test
    @DisplayName("a reference whose target, value column and label column are readable is AVAILABLE")
    void availableReference() {
        assertThat(ReferencePolicy.accessOf(reference("id", "name"), readable()))
                .isEqualTo(ReferenceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("each missing condition alone makes the reference NO_PERMISSION")
    void eachMissingConditionDenies() {
        ReferenceTarget target = reference("id", "name");

        assertThat(ReferencePolicy.accessOf(target, Optional.empty()))
                .as("参照先のテーブルの判定の材料が無い")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThat(ReferencePolicy.accessOf(target, Optional.of(view(MainPermission.NONE, Map.of()))))
                .as("参照先のテーブルが NONE")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThat(ReferencePolicy.accessOf(
                        target, Optional.of(view(MainPermission.READ, Map.of("id", MainPermission.NONE)))))
                .as("値のカラムが NONE")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThat(ReferencePolicy.accessOf(
                        target, Optional.of(view(MainPermission.READ, Map.of("name", MainPermission.NONE)))))
                .as("ラベルのカラムが NONE")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThat(ReferencePolicy.accessOf(reference("missing", "name"), readable()))
                .as("値のカラムが DSL に無い")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret", "blob"})
    @DisplayName("a password or binary value column cannot be passed as text and is NO_PERMISSION")
    void passwordOrBinaryValueColumnDenies(String valueColumn) {
        assertThat(ReferencePolicy.accessOf(reference(valueColumn, "name"), readable()))
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
    }

    @Test
    @DisplayName("a password or binary label column is still AVAILABLE and is neither selected nor sorted")
    void passwordOrBinaryLabelColumn() {
        for (String label : List.of("secret", "blob")) {
            ReferenceTarget target = reference("id", label);
            TableAccessView view = readable().orElseThrow();

            assertThat(ReferencePolicy.accessOf(target, Optional.of(view))).isEqualTo(ReferenceStatus.AVAILABLE);
            ReferenceReadSpec spec = ReferencePolicy.optionsSpec(target, view);
            assertThat(spec.displayColumns()).isEmpty();
            assertThat(spec.order()).extracting(SortKey::column).containsExactly("id");
        }
    }

    @Test
    @DisplayName("a lookup is NO_PERMISSION when any list column is NONE or no search field is left")
    void lookupDenials() {
        assertThat(ReferencePolicy.accessOf(standardLookup(), readable())).isEqualTo(ReferenceStatus.AVAILABLE);
        assertThat(ReferencePolicy.accessOf(
                        standardLookup(), Optional.of(view(MainPermission.READ, Map.of("code", MainPermission.NONE)))))
                .as("一覧の列が NONE（検索の条件 name は残る）")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        ReferenceTarget onlyUnusable = lookup(
                "name",
                List.of(
                        new LookupSearchItem("amount", SearchOperator.CONTAINS),
                        new LookupSearchItem("name", SearchOperator.RANGE),
                        new LookupSearchItem("secret", SearchOperator.EQUALS)),
                List.of(new LookupListItem("name", 1)));
        assertThat(ReferencePolicy.accessOf(onlyUnusable, readable()))
                .as("使える検索の条件が残らない")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
    }

    @Test
    @DisplayName("usable search fields keep EQUALS and CONTAINS on text only, in the DSL order")
    void usableSearchFields() {
        ReferenceTarget target = lookup(
                null,
                List.of(
                        new LookupSearchItem("code", SearchOperator.EQUALS),
                        new LookupSearchItem("amount", SearchOperator.CONTAINS),
                        new LookupSearchItem("name", SearchOperator.CONTAINS),
                        new LookupSearchItem("id", SearchOperator.RANGE),
                        new LookupSearchItem("id", SearchOperator.CHOICE),
                        new LookupSearchItem("id", SearchOperator.IN),
                        new LookupSearchItem("blob", SearchOperator.EQUALS),
                        new LookupSearchItem("secret", SearchOperator.EQUALS),
                        new LookupSearchItem("amount", SearchOperator.EQUALS)),
                List.of(new LookupListItem("name", 1)));

        List<PickerSearchField> fields = ReferencePolicy.usableSearchFields(
                target, view(MainPermission.READ, Map.of("code", MainPermission.NONE)));

        assertThat(fields)
                .extracting(PickerSearchField::column, PickerSearchField::operator, PickerSearchField::valueType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("name", SearchMode.CONTAINS, ValueType.STRING),
                        org.assertj.core.groups.Tuple.tuple("amount", SearchMode.EQUALS, ValueType.DECIMAL));
        assertThat(ReferencePolicy.usableSearchFields(
                        reference("id", "name"), readable().orElseThrow()))
                .isEmpty();
    }

    @Test
    @DisplayName("the label column of a lookup is labelColumn if set and otherwise the first list column by order")
    void lookupLabelColumn() {
        assertThat(standardLookup().labelColumn()).isEqualTo("name");
        assertThat(standardLookup().lookupList())
                .extracting(LookupListItem::column)
                .containsExactly("name", "code");
        ReferenceTarget sameOrder = lookup(
                null,
                List.of(new LookupSearchItem("name", SearchOperator.EQUALS)),
                List.of(new LookupListItem("code", 1), new LookupListItem("name", 1)));
        assertThat(sameOrder.labelColumn()).as("同じ order は DSL の並び").isEqualTo("code");
        ReferenceTarget explicit = lookup(
                "amount",
                List.of(new LookupSearchItem("name", SearchOperator.EQUALS)),
                List.of(new LookupListItem("code", 1)));
        assertThat(explicit.labelColumn()).isEqualTo("amount");
        assertThat(reference("id", "name").labelColumn()).isEqualTo("name");
    }

    @Test
    @DisplayName("the picker lists columns by order with value types and never a column missing from the view")
    void pickerOf() {
        TableAccessView view = readable().orElseThrow();

        PickerInfo picker = ReferencePolicy.pickerOf(standardLookup(), view);

        assertThat(picker.listColumns())
                .extracting(PickerListColumn::column, PickerListColumn::valueType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("name", ValueType.STRING),
                        org.assertj.core.groups.Tuple.tuple("code", ValueType.STRING));
        assertThat(picker.searchFields()).extracting(PickerSearchField::column).containsExactly("name", "code");
        PickerInfo guarded = ReferencePolicy.pickerOf(
                standardLookup(), view(MainPermission.READ, Map.of("code", MainPermission.NONE)));
        assertThat(guarded.listColumns()).extracting(PickerListColumn::column).containsExactly("name");
        assertThat(guarded.searchFields()).extracting(PickerSearchField::column).containsExactly("name");
    }

    @Test
    @DisplayName("the fixed order is label, value and then the primary key, without repeating a column")
    void fixedOrder() {
        TableAccessView view = readable().orElseThrow();

        assertThat(ReferencePolicy.fixedOrder(reference("code", "name"), view))
                .containsExactly(
                        new SortKey("name", SortDirection.ASC),
                        new SortKey("code", SortDirection.ASC),
                        new SortKey("id", SortDirection.ASC));
        assertThat(ReferencePolicy.fixedOrder(reference("id", "id"), view))
                .as("ラベルと値が同じカラム")
                .containsExactly(new SortKey("id", SortDirection.ASC));
        DslTable noKey =
                table("log", false, List.of(), column("v").build(), column("l").build());
        TableAccessView noKeyView = BusinessDataDslFixture.view(noKey, MainPermission.READ, Map.of());
        ReferenceTarget logTarget = ReferenceTarget.from(
                        "sample",
                        new OptionSource(OptionSourceKind.REFERENCE, List.of(), "log", "v", "l", List.of(), List.of()))
                .orElseThrow();
        assertThat(ReferencePolicy.fixedOrder(logTarget, noKeyView))
                .as("主キーの無い参照先")
                .extracting(SortKey::column)
                .containsExactly("l", "v");
    }

    @Test
    @DisplayName("the options spec reads 201 rows, excludes null values, counts nothing and limits values to 10,000")
    void optionsSpec() {
        ReferenceReadSpec spec = ReferencePolicy.optionsSpec(
                reference("code", "name"), readable().orElseThrow());

        assertThat(spec.valueColumn()).isEqualTo("code");
        assertThat(spec.displayColumns()).containsExactly("name");
        assertThat(spec.excludeNullValue()).isTrue();
        assertThat(spec.count()).isFalse();
        assertThat(spec.maxRows()).isEqualTo(201);
        assertThat(spec.offset()).isZero();
        assertThat(spec.maxValueLength()).isEqualTo(10_000);
        assertThat(ReferencePolicy.optionsSpec(
                                reference("name", "name"), readable().orElseThrow())
                        .displayColumns())
                .as("値と同じラベルのカラムも、ラベルとしては切る選び方で別に選ぶ（BR7.3）")
                .containsExactly("name");
    }

    @Test
    @DisplayName("the picker spec reads 21 rows from the offset with the label and list columns, without duplicates")
    void pickerSpec() {
        ReferenceTarget target = lookup(
                "name",
                List.of(new LookupSearchItem("name", SearchOperator.CONTAINS)),
                List.of(
                        new LookupListItem("code", 1),
                        new LookupListItem("name", 2),
                        new LookupListItem("secret", 3),
                        new LookupListItem("blob", 4)));

        ReferenceReadSpec spec = ReferencePolicy.pickerSpec(target, readable().orElseThrow(), 40);

        assertThat(spec.valueColumn()).isEqualTo("id");
        assertThat(spec.displayColumns())
                .as("ラベルを先に、一覧の列を順に。password・バイナリは選ばず重ねない")
                .containsExactly("name", "code");
        assertThat(spec.maxRows()).isEqualTo(21);
        assertThat(spec.offset()).isEqualTo(40);
        assertThat(spec.excludeNullValue()).isTrue();
        assertThat(spec.count()).isFalse();
        assertThat(spec.order()).extracting(SortKey::column).containsExactly("name", "id");
        assertThat(ReferencePolicy.pickerSpec(target, view(MainPermission.READ, Map.of("code", MainPermission.NONE)), 0)
                        .displayColumns())
                .as("判定の材料に無い列は選ばない（BR3.5 の守り）")
                .containsExactly("name");
    }

    @Test
    @DisplayName("the label spec reads one row with the label and the check spec reads 201 rows of the value only")
    void labelAndCheckSpecs() {
        ReferenceReadSpec label =
                ReferencePolicy.labelSpec(reference("code", "name"), readable().orElseThrow());
        ReferenceReadSpec masked = ReferencePolicy.labelSpec(
                reference("code", "secret"), readable().orElseThrow());
        ReferenceReadSpec check =
                ReferencePolicy.checkSpec(reference("code", "name"), readable().orElseThrow());

        assertThat(label.maxRows()).isEqualTo(1);
        assertThat(label.displayColumns()).containsExactly("name");
        assertThat(label.order()).extracting(SortKey::column).containsExactly("name", "code", "id");
        assertThat(masked.displayColumns()).as("password のラベルは読まない").isEmpty();
        assertThat(check.maxRows()).isEqualTo(201);
        assertThat(check.displayColumns()).isEmpty();
        assertThat(check.excludeNullValue()).isTrue();
        assertThat(check.order()).extracting(SortKey::column).containsExactly("name", "code", "id");
        assertThat(ReferenceReadSpec.CHECK_COMPARED_ROWS).isEqualTo(200);
    }

    @Test
    @DisplayName("distinct by value keeps the first row of each strictly equal value in order")
    void distinctByValue() {
        record Row(String label, BoundValue value) {}
        List<Row> rows = List.of(
                new Row("一", text("A")),
                new Row("二", text("a")),
                new Row("三", text("A")),
                new Row("四", text("A ")),
                new Row("五", null),
                new Row("六", null));

        assertThat(ReferencePolicy.distinctByValue(rows, Row::value))
                .extracting(Row::label)
                .containsExactly("一", "二", "四", "五", "六");
    }

    @Test
    @DisplayName("same reference value is strict for text and numeric for decimals")
    void sameReferenceValue() {
        assertThat(ReferencePolicy.sameReferenceValue(text("abc"), text("abc"))).isTrue();
        assertThat(ReferencePolicy.sameReferenceValue(text("abc"), text("ABC"))).isFalse();
        assertThat(ReferencePolicy.sameReferenceValue(text("abc"), text("abc ")))
                .isFalse();
        assertThat(ReferencePolicy.sameReferenceValue(decimal("1.0"), decimal("1")))
                .isTrue();
        assertThat(ReferencePolicy.sameReferenceValue(decimal("1.01"), decimal("1")))
                .isFalse();
        assertThat(ReferencePolicy.sameReferenceValue(
                        new BoundValue(ValueType.BIG_INTEGER, new BigDecimal("9223372036854775808")),
                        new BoundValue(ValueType.BIG_INTEGER, new BigDecimal("9223372036854775808.0"))))
                .isTrue();
        assertThat(ReferencePolicy.sameReferenceValue(new BoundValue(ValueType.INTEGER, 1L), decimal("1")))
                .as("型が違う")
                .isFalse();
        OffsetDateTime utc = OffsetDateTime.of(2026, 10, 30, 0, 0, 0, 0, ZoneOffset.UTC);
        assertThat(ReferencePolicy.sameReferenceValue(
                        new BoundValue(ValueType.DATETIME_TZ, utc),
                        new BoundValue(ValueType.DATETIME_TZ, utc.withOffsetSameInstant(ZoneOffset.ofHours(9)))))
                .isTrue();
        assertThat(ReferencePolicy.sameReferenceValue(null, text("a")))
                .as("読めない値")
                .isFalse();
        assertThat(ReferencePolicy.sameReferenceValue(
                        new BoundValue(ValueType.STRING, null), new BoundValue(ValueType.STRING, null)))
                .as("NULL")
                .isFalse();
    }

    @Test
    @DisplayName("the picker info refuses empty search fields or list columns")
    void pickerInfoNeedsBoth() {
        assertThatThrownBy(() -> new PickerInfo(List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static BoundValue text(String value) {
        return new BoundValue(ValueType.STRING, value);
    }

    private static BoundValue decimal(String value) {
        return new BoundValue(ValueType.DECIMAL, new BigDecimal(value));
    }
}
