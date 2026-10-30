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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 他テーブル参照の値の型の単体テスト（BR3.6・BR4.3・BR10.1、entities.md の制約）。 */
class ReferenceTypesTest {

    private static final String SECRET = "社外秘の値<script>";

    @Test
    @org.junit.jupiter.api.DisplayName("the definition purpose reads references only for create and update")
    void definitionPurpose() {
        ColumnAccess createOnly = withEditable(true, false);

        assertThat(DefinitionPurpose.VIEW.readsReferences()).isFalse();
        assertThat(DefinitionPurpose.CREATE.readsReferences()).isTrue();
        assertThat(DefinitionPurpose.UPDATE.readsReferences()).isTrue();
        assertThat(DefinitionPurpose.VIEW.editable(createOnly)).isFalse();
        assertThat(DefinitionPurpose.CREATE.editable(createOnly)).isTrue();
        assertThat(DefinitionPurpose.UPDATE.editable(createOnly)).isFalse();
        assertThat(DefinitionPurpose.UPDATE.editable(withEditable(false, true))).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("only REFERENCE and LOOKUP options make a target")
    void targetFromOptions() {
        assertThat(ReferenceTarget.from("s", null)).isEmpty();
        assertThat(ReferenceTarget.from("s", BusinessDataDslFixture.fixed("A"))).isEmpty();
        ReferenceTarget reference =
                ReferenceTarget.from("s", BusinessDataDslFixture.reference()).orElseThrow();
        assertThat(reference.kind()).isEqualTo(OptionSourceKind.REFERENCE);
        assertThat(reference.lookupSearch()).isEmpty();
        assertThat(reference.lookupList()).isEmpty();
        assertThatThrownBy(() -> new ReferenceTarget(OptionSourceKind.FIXED, "s", "t", "v", "l", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "value holding types never print values, labels or names other than table and kind")
    void textFormsHideValues() {
        ReferenceOption option = new ReferenceOption(SECRET, SECRET);
        ReferenceTarget target = ReferenceTarget.from(
                        "secret_schema",
                        new OptionSource(
                                OptionSourceKind.LOOKUP,
                                List.of(),
                                "dept",
                                "secret_value_column",
                                null,
                                List.of(new LookupSearchItem("secret_search", SearchOperator.EQUALS)),
                                List.of(new LookupListItem("secret_list", 1))))
                .orElseThrow();
        ReferenceField field = ReferenceField.choices(List.of(option, new ReferenceOption(SECRET, null)));

        assertThat(option.toString()).doesNotContain(SECRET).contains("***");
        assertThat(new ReferenceOption(SECRET, null).toString())
                .contains("label=null")
                .doesNotContain(SECRET);
        assertThat(field.toString()).doesNotContain(SECRET).contains("options=2");
        assertThat(target.toString())
                .isEqualTo("ReferenceTarget[kind=LOOKUP, table=dept]")
                .doesNotContain("secret");
        assertThat(ReferenceReadSpec.options("v", List.of("l"), List.of()).toString())
                .contains("maxRows=201");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a field that is not AVAILABLE carries neither options nor a picker")
    void fieldInvariants() {
        PickerInfo picker = new PickerInfo(
                List.of(new PickerSearchField("n", new DisplayName("名", "Name"), SearchMode.EQUALS, ValueType.STRING)),
                List.of(new PickerListColumn("n", new DisplayName("名", "Name"), ValueType.STRING)));

        assertThat(ReferenceField.noPermission(OptionSourceKind.LOOKUP).picker())
                .isNull();
        assertThat(ReferenceField.tooMany().options()).isEmpty();
        assertThat(ReferenceField.lookup(picker).status()).isEqualTo(ReferenceStatus.AVAILABLE);
        assertThat(ReferenceField.choices(List.of()).status()).as("空の参照先").isEqualTo(ReferenceStatus.AVAILABLE);
        assertThatThrownBy(() -> new ReferenceField(
                        OptionSourceKind.REFERENCE,
                        ReferenceStatus.NO_PERMISSION,
                        List.of(new ReferenceOption("1", "一")),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new ReferenceField(OptionSourceKind.LOOKUP, ReferenceStatus.NO_PERMISSION, List.of(), picker))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceField(OptionSourceKind.LOOKUP, ReferenceStatus.TOO_MANY, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new ReferenceField(OptionSourceKind.REFERENCE, ReferenceStatus.AVAILABLE, List.of(), picker))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("choices accept 200 options and refuse 201")
    void choicesBoundary() {
        List<ReferenceOption> twoHundred = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> new ReferenceOption(String.valueOf(i), "ラベル" + i))
                .toList();
        List<ReferenceOption> twoHundredOne = new java.util.ArrayList<>(twoHundred);
        twoHundredOne.add(new ReferenceOption("200", "ラベル200"));

        assertThat(ReferenceField.choices(twoHundred).options()).hasSize(200);
        assertThatThrownBy(() -> ReferenceField.choices(twoHundredOne)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a picker search field takes only EQUALS or CONTAINS on text")
    void pickerSearchFieldOperators() {
        DisplayName name = new DisplayName("名", "Name");

        assertThatThrownBy(() -> new PickerSearchField("n", name, SearchMode.RANGE, ValueType.INTEGER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PickerSearchField("n", name, SearchMode.CONTAINS, ValueType.INTEGER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PickerSearchField("n", name, SearchMode.EQUALS, ValueType.INTEGER).operator())
                .isEqualTo(SearchMode.EQUALS);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a read spec refuses out of range rows, offsets and lengths")
    void readSpecRanges() {
        List<SortKey> order = List.of(new SortKey("v", SortDirection.ASC));

        assertThatThrownBy(() -> new ReferenceReadSpec("v", List.of(), true, order, false, 0, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceReadSpec("v", List.of(), true, order, false, 1, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceReadSpec("v", List.of(), true, order, false, 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ReferenceReadSpec.MAX_VALUE_LENGTH).isEqualTo(ColumnWriteRulesFactory.MAX_VALUE_LENGTH);
        assertThat(ReferenceReadSpec.OPTION_ROWS).isEqualTo(201);
    }

    private static ColumnAccess withEditable(boolean onCreate, boolean onUpdate) {
        ColumnAccess base = BusinessDataDslFixture.access(ValueType.STRING, "varchar");
        return new ColumnAccess(
                base.column(),
                base.displayName(),
                base.main(),
                base.valueType(),
                base.dbTypeName(),
                base.primaryKey(),
                base.listVisible(),
                base.listOrder(),
                base.width(),
                base.listFormat(),
                base.detailVisible(),
                base.search(),
                base.searchDefault(),
                base.searchCollapsed(),
                base.sortable(),
                base.defaultSort(),
                onCreate,
                onUpdate,
                base.masked(),
                base.validation());
    }
}
