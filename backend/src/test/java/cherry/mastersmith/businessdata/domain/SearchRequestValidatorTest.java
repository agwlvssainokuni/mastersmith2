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
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.fixed;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.kind;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.simpleReader;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 一覧と検索・詳細の本文の確かめ（BR1.6・BR4・BR5.1・BR5.2・BR6.2、NFR4.5）の単体テスト。 */
class SearchRequestValidatorTest {

    private static final DslTable ITEM = table(
            "item",
            false,
            List.of("code", "branch"),
            column("code").dbType("int4").search(SearchOperator.EQUALS).build(),
            column("branch").build(),
            column("name").search(SearchOperator.CONTAINS).build(),
            column("price").dbType("numeric").search(SearchOperator.RANGE).build(),
            column("since").dbType("date").search(SearchOperator.RANGE).build(),
            column("kind")
                    .search(SearchOperator.IN)
                    .options(fixed("A", "B", "C"))
                    .build(),
            column("active").dbType("bool").search(SearchOperator.CHOICE).build(),
            column("hidden").search(SearchOperator.EQUALS).build(),
            column("pw").form(FormPart.PASSWORD).search(SearchOperator.EQUALS).build());

    private static final TableAccessView VIEW = view();

    private static TableAccessView view() {
        Map<String, MainPermission> main = new HashMap<>();
        ITEM.columns().keySet().forEach(name -> main.put(name, MainPermission.READ));
        main.put("hidden", MainPermission.NONE);
        return TableAccessPolicy.evaluate(BusinessDataDslFixture.input(
                ITEM,
                MainPermission.READ,
                main,
                Map.of(
                        "code", kind(ValueType.INTEGER),
                        "price", kind(ValueType.DECIMAL),
                        "since", kind(ValueType.DATE),
                        "active", kind(ValueType.BOOLEAN)),
                false,
                false,
                false));
    }

    private static SearchValidation search(List<RawCondition> conditions, RawSort sort, String page, String size) {
        return SearchRequestValidator.validateSearch(
                VIEW,
                new SearchInput(
                        conditions,
                        sort,
                        page == null ? null : new BigDecimal(page),
                        size == null ? null : new BigDecimal(size)),
                simpleReader());
    }

    private static RawCondition value(String column, String value) {
        return new RawCondition(column, value, null, null, null);
    }

    private static List<BusinessDataFieldError> errors(SearchValidation validation) {
        assertThat(validation).isInstanceOf(SearchValidation.Invalid.class);
        return ((SearchValidation.Invalid) validation).errors();
    }

    @Test
    @DisplayName("NONE columns, columns outside the DSL, password columns and repeated columns are all NOT_ALLOWED")
    void notAllowedColumns() {
        SearchValidation result = search(
                List.of(
                        value("hidden", "x"),
                        value("nowhere", "x"),
                        value("pw", "x"),
                        value("branch", "x"),
                        value("name", "a"),
                        value("name", "b"),
                        value("x'; DROP TABLE item; --", "x")),
                null,
                null,
                null);

        assertThat(errors(result))
                .extracting(BusinessDataFieldError::field, BusinessDataFieldError::reason)
                .containsExactly(
                        tuple("", BusinessDataFieldErrorReason.NOT_ALLOWED),
                        tuple("", BusinessDataFieldErrorReason.NOT_ALLOWED),
                        tuple("pw", BusinessDataFieldErrorReason.NOT_ALLOWED),
                        tuple("branch", BusinessDataFieldErrorReason.NOT_ALLOWED),
                        tuple("name", BusinessDataFieldErrorReason.NOT_ALLOWED),
                        tuple("", BusinessDataFieldErrorReason.NOT_ALLOWED));
    }

    @Test
    @DisplayName("a name that the table does not show is never echoed: a 64 KB name and quotes give an empty field")
    void unknownNamesAreNotEchoed() {
        String longName = "n".repeat(64 * 1024);
        String quoted = "x'; DROP TABLE item; --\n`\"";

        SearchValidation conditions = search(
                List.of(value(longName, "x"), value(quoted, "x"), value("hidden", "x"), value(null, "x")),
                new RawSort(longName, "ASC"),
                null,
                null);
        SearchValidation sortOnly = search(List.of(), new RawSort(quoted, "ASC"), null, null);
        Map<String, String> badKey = new LinkedHashMap<>();
        badKey.put("code", "12");
        badKey.put("branch", "a");
        badKey.put(longName, "1");
        badKey.put(quoted, "1");
        badKey.put("hidden", "1");
        KeyValidation key = SearchRequestValidator.validateKey(VIEW, new KeyInput(badKey), simpleReader());

        List<BusinessDataFieldError> all = new java.util.ArrayList<>(errors(conditions));
        all.addAll(errors(sortOnly));
        all.addAll(((KeyValidation.Invalid) key).errors());
        assertThat(all).hasSize(4 + 1 + 1 + 3);
        assertThat(all)
                .extracting(BusinessDataFieldError::field)
                .allSatisfy(field -> assertThat(field).isEmpty());
        assertThat(all.toString()).as("TRACE に出る文字列にも名前が入らない").hasSizeLessThan(2_000);
    }

    @Test
    @DisplayName("a name of a column that the table shows is still echoed, so the screen can point at it")
    void knownNamesAreEchoed() {
        SearchValidation result =
                search(List.of(value("pw", "x"), value("name", "a"), value("name", "b")), null, null, null);
        Map<String, String> missing = new LinkedHashMap<>();
        missing.put("code", "12");
        missing.put("name", "not a key column");
        KeyValidation key = SearchRequestValidator.validateKey(VIEW, new KeyInput(missing), simpleReader());

        assertThat(errors(result)).extracting(BusinessDataFieldError::field).containsExactly("pw", "name");
        assertThat(((KeyValidation.Invalid) key).errors())
                .extracting(BusinessDataFieldError::field, BusinessDataFieldError::reason)
                .containsExactlyInAnyOrder(
                        tuple("branch", BusinessDataFieldErrorReason.KEY_MISMATCH),
                        tuple("name", BusinessDataFieldErrorReason.KEY_MISMATCH));
    }

    @Test
    @DisplayName("values that cannot be read as the column type are INVALID_VALUE and errors are collected together")
    void invalidValues() {
        SearchValidation result = search(
                List.of(
                        value("code", "12a"),
                        new RawCondition("since", null, "2026-13-01", null, null),
                        new RawCondition("active", null, null, null, List.of("yes"))),
                new RawSort("price", "SIDEWAYS"),
                null,
                null);

        assertThat(errors(result))
                .extracting(BusinessDataFieldError::reason)
                .containsExactly(
                        BusinessDataFieldErrorReason.INVALID_VALUE,
                        BusinessDataFieldErrorReason.INVALID_VALUE,
                        BusinessDataFieldErrorReason.NOT_A_CHOICE,
                        BusinessDataFieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("a range whose lower end is above the upper end is RANGE_REVERSED and equal ends are accepted")
    void rangeDirection() {
        assertThat(errors(search(List.of(new RawCondition("price", null, "10.5", "10.49", null)), null, null, null)))
                .extracting(BusinessDataFieldError::reason)
                .containsExactly(BusinessDataFieldErrorReason.RANGE_REVERSED);

        SearchValidation same =
                search(List.of(new RawCondition("since", null, "2026-01-01", "2026-01-01", null)), null, null, null);
        assertThat(same).isInstanceOf(SearchValidation.Valid.class);
        SearchCondition condition = ((SearchValidation.Valid) same).conditions().getFirst();
        assertThat(condition.mode()).isEqualTo(SearchMode.RANGE);
        assertThat(condition.from().value()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("choices are de-duplicated, must be in the list and empty values make no condition")
    void choices() {
        SearchValidation ok = search(
                List.of(
                        new RawCondition("kind", null, null, null, List.of("A", "B", "A", "")),
                        value("name", ""),
                        new RawCondition("price", null, "", null, null)),
                null,
                null,
                null);
        assertThat(ok).isInstanceOf(SearchValidation.Valid.class);
        assertThat(((SearchValidation.Valid) ok).conditions()).hasSize(1);
        assertThat(((SearchValidation.Valid) ok).conditions().getFirst().values())
                .hasSize(2);

        assertThat(errors(search(
                        List.of(new RawCondition("kind", null, null, null, List.of("A", "Z"))), null, null, null)))
                .extracting(BusinessDataFieldError::reason)
                .containsExactly(BusinessDataFieldErrorReason.NOT_A_CHOICE);
    }

    @ParameterizedTest(name = "page={0} size={1} valid={2}")
    @CsvSource({
        "1, 20, true",
        "1000000, 100, true",
        "1000001, 20, false",
        "0, 20, false",
        "1.5, 20, false",
        "2.0, 50, true",
        "1, 30, false",
        "1, 1000000000000, false",
        ", , true"
    })
    @DisplayName("the page is 1 to 1,000,000 and the size is 20, 50 or 100, with no field errors on failure")
    void pageAndSize(String page, String size, boolean valid) {
        SearchValidation result = search(List.of(), null, page, size);

        if (valid) {
            assertThat(result).isInstanceOf(SearchValidation.Valid.class);
        } else {
            assertThat(errors(result)).isEmpty();
        }
    }

    @Test
    @DisplayName("a value of 1,000 code points is accepted and 1,001 code points is INVALID_VALUE")
    void valueLength() {
        String emoji = "😀";
        assertThat(search(List.of(value("name", emoji.repeat(1000))), null, null, null))
                .isInstanceOf(SearchValidation.Valid.class);
        assertThat(errors(search(List.of(value("name", emoji.repeat(1001))), null, null, null)))
                .extracting(BusinessDataFieldError::reason)
                .containsExactly(BusinessDataFieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("the sort column must be sortable and the direction defaults to ascending")
    void sort() {
        SearchValidation ok = search(List.of(), new RawSort("price", null), "3", "50");

        assertThat(ok).isInstanceOf(SearchValidation.Valid.class);
        PageRequest page = ((SearchValidation.Valid) ok).page();
        assertThat(page.sort()).isEqualTo(new SortKey("price", SortDirection.ASC));
        assertThat(page.offset()).isEqualTo(100L);
        assertThat(errors(search(List.of(), new RawSort("pw", "ASC"), null, null)))
                .extracting(BusinessDataFieldError::reason)
                .containsExactly(BusinessDataFieldErrorReason.NOT_ALLOWED);
    }

    @Test
    @DisplayName("the key must have exactly the primary key columns, read as their types")
    void keyValidation() {
        Map<String, String> good = new LinkedHashMap<>();
        good.put("code", "12");
        good.put("branch", "a/b%c?d#e f'\"");
        KeyValidation valid = SearchRequestValidator.validateKey(VIEW, new KeyInput(good), simpleReader());
        assertThat(valid).isInstanceOf(KeyValidation.Valid.class);
        assertThat(((KeyValidation.Valid) valid).key().values().get("code").value())
                .isEqualTo(12L);
        assertThat(((KeyValidation.Valid) valid).key().values().get("branch").value())
                .isEqualTo("a/b%c?d#e f'\"");

        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("code", "x");
        bad.put("extra", "1");
        KeyValidation invalid = SearchRequestValidator.validateKey(VIEW, new KeyInput(bad), simpleReader());
        assertThat(((KeyValidation.Invalid) invalid).errors())
                .extracting(BusinessDataFieldError::field, BusinessDataFieldError::reason)
                .containsExactlyInAnyOrder(
                        tuple("branch", BusinessDataFieldErrorReason.KEY_MISMATCH),
                        tuple("", BusinessDataFieldErrorReason.KEY_MISMATCH),
                        tuple("code", BusinessDataFieldErrorReason.INVALID_VALUE));

        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("code", null);
        nullValue.put("branch", "x".repeat(1001));
        assertThat(((KeyValidation.Invalid)
                                SearchRequestValidator.validateKey(VIEW, new KeyInput(nullValue), simpleReader()))
                        .errors())
                .extracting(BusinessDataFieldError::reason)
                .containsOnly(BusinessDataFieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("the key is not checked for a table whose detail is not allowed")
    void keyNeedsDetail() {
        DslTable noKey = table("log", false, List.of(), column("a").build());
        TableAccessView view = BusinessDataDslFixture.view(noKey, MainPermission.READ, Map.of());

        assertThatThrownBy(
                        () -> SearchRequestValidator.validateKey(view, new KeyInput(Map.of("a", "1")), simpleReader()))
                .isInstanceOf(IllegalStateException.class);
        // 読み取りの理由は5つのまま（NFR4.5）。U2（Intent 261016-business-data-views の B4）は書き込みの理由をこの後ろに足した。
        assertThat(EnumSet.range(BusinessDataFieldErrorReason.NOT_ALLOWED, BusinessDataFieldErrorReason.KEY_MISMATCH))
                .containsExactly(
                        BusinessDataFieldErrorReason.NOT_ALLOWED,
                        BusinessDataFieldErrorReason.INVALID_VALUE,
                        BusinessDataFieldErrorReason.RANGE_REVERSED,
                        BusinessDataFieldErrorReason.NOT_A_CHOICE,
                        BusinessDataFieldErrorReason.KEY_MISMATCH);
    }
}
