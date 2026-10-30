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
package cherry.mastersmith.businessdata.web;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.kind;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldErrorReason;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.ReferenceFailureReason;
import cherry.mastersmith.businessdata.domain.ReferenceRow;
import cherry.mastersmith.businessdata.domain.ReferenceRows;
import cherry.mastersmith.businessdata.domain.ReferenceRowsQuery;
import cherry.mastersmith.businessdata.domain.ReferenceValue;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.businessdata.service.BusinessDataReadService;
import cherry.mastersmith.businessdata.service.DefinitionResult;
import cherry.mastersmith.businessdata.service.ReadRejected;
import cherry.mastersmith.businessdata.service.ReferenceLookup;
import cherry.mastersmith.businessdata.service.ReferenceRowsResult;
import cherry.mastersmith.businessdata.service.RowReadResult;
import cherry.mastersmith.businessdata.service.SearchResult;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 結果の型から応答・業務エラーへの変換（BR1.1〜BR1.9・BR2.8・BR9.2・BR10.3）の単体テスト。 */
class BusinessDataReadControllerTest {

    private static final ProblemType ACCESS_DENIED = new ProblemType(
            "ACCESS_DENIED", 403, new LocalizedText("拒否", "Denied"), new LocalizedText("拒否", "Denied"), null);

    private static final DslTable ITEM = table(
            "item",
            false,
            List.of("code"),
            column("code")
                    .dbType("int4")
                    .list(true, 1)
                    .search(SearchOperator.EQUALS)
                    .build(),
            column("name")
                    .list(true, 2)
                    .search(SearchOperator.CONTAINS)
                    .defaultSort(SortDirection.DESC)
                    .build(),
            column("pw").form(FormPart.PASSWORD).list(false, null).build());

    private static final TableAccessView VIEW =
            BusinessDataDslFixture.view(ITEM, MainPermission.READ, Map.of("code", kind(ValueType.INTEGER)));

    private BusinessDataReadService service;

    private ReferenceLookup references;

    private BusinessDataReadController controller;

    @BeforeEach
    void setUp() {
        service = mock(BusinessDataReadService.class);
        references = mock(ReferenceLookup.class);
        BusinessDataRequestContext context = mock(BusinessDataRequestContext.class);
        when(context.currentUserId()).thenReturn(42L);
        ProblemTypeRegistry registry = mock(ProblemTypeRegistry.class);
        when(registry.findByCode("ACCESS_DENIED")).thenReturn(Optional.of(ACCESS_DENIED));
        controller = new BusinessDataReadController(service, references, context, registry);
    }

    @Test
    @DisplayName("the definition carries the read items and the validation items of every column (U2, BR14.1)")
    void definition() {
        when(service.definition(eq(42L), any(), eq(DefinitionPurpose.VIEW)))
                .thenReturn(new DefinitionResult.Found(VIEW, true));

        TableDefinitionResponse response = controller.definition(
                new DefinitionRequest("sample", "item", new DefinitionIdBody(BusinessDataDslFixture.HASH, 7L)));

        verify(service)
                .definition(
                        42L,
                        new TableQuery("sample", "item", new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L)),
                        DefinitionPurpose.VIEW);
        assertThat(response.definitionChanged()).isTrue();
        assertThat(response.definitionId()).isEqualTo(new DefinitionIdBody(BusinessDataDslFixture.HASH, 7L));
        assertThat(response.listColumns())
                .extracting(ColumnDefinitionResponse::column)
                .containsExactly("code", "name");
        assertThat(response.detailColumns())
                .extracting(ColumnDefinitionResponse::column)
                .containsExactly("code", "name", "pw");
        assertThat(response.detailColumns().get(2).masked()).isTrue();
        assertThat(response.listColumns()).allSatisfy(c -> {
            assertThat(c.formPart()).as("検証の材料はすべてのカラムで出す（U2 が埋める）").isEqualTo("text");
            assertThat(c.nullable()).isTrue();
            assertThat(c.required()).as("READ のカラムは書けないため偽").isFalse();
            assertThat(c.choices()).isEmpty();
            assertThat(c.maxLength()).as("書けないカラムは既定").isNull();
        });
        assertThat(response.searchFields())
                .extracting(SearchFieldResponse::column)
                .containsExactly("code", "name");
        assertThat(response.primaryKeyColumns()).containsExactly("code");
        assertThat(response.hasPrimaryKey()).isTrue();
        assertThat(response.defaultSort()).containsExactly(new SortKeyBody("name", "DESC"));
        assertThat(response.sortableColumns()).containsExactly("code", "name");
        assertThat(response.writeConfigured()).isFalse();
        assertThat(response.rowLabelColumns()).containsExactly("code");
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
        "DSL_NOT_APPLIED, 404, BUSINESS_DSL_NOT_APPLIED",
        "TABLE_NOT_FOUND, 404, BUSINESS_TABLE_NOT_FOUND",
        "ACCESS_DENIED, 403, ACCESS_DENIED",
        "ROW_OPERATION_NOT_ALLOWED, 409, BUSINESS_ROW_OPERATION_NOT_ALLOWED",
        "ROW_NOT_FOUND, 404, BUSINESS_ROW_NOT_FOUND",
        "ROW_NOT_UNIQUE, 409, BUSINESS_ROW_NOT_UNIQUE",
        "TARGET_DB_UNAVAILABLE, 503, BUSINESS_TARGET_DB_UNAVAILABLE",
        "TARGET_DB_TIMEOUT, 503, BUSINESS_TARGET_DB_TIMEOUT",
        "TARGET_DB_MISMATCH, 409, BUSINESS_TARGET_DB_MISMATCH",
        "VALIDATION_FAILED, 400, VALIDATION_FAILED"
    })
    @DisplayName("each rejection becomes its status and code, and only 400 carries definitionChanged")
    void rejections(ReadRejection rejection, int status, String code) {
        when(service.get(anyLong(), any(), any())).thenReturn(new ReadRejected(rejection, List.of(), true));

        assertThatThrownBy(() -> controller.get(new RowGetRequest("sample", "item", null, Map.of("code", "1"))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getProblemType().status()).isEqualTo(status);
                    assertThat(e.getProblemType().code()).isEqualTo(code);
                    if (rejection == ReadRejection.VALIDATION_FAILED) {
                        assertThat(e.getProperties()).containsOnlyKeys("definitionChanged");
                        assertThat(e.getProperties()).containsEntry("definitionChanged", true);
                    } else {
                        assertThat(e.getProperties()).isEmpty();
                    }
                    assertThat(e.getDetail()).isNull();
                });
    }

    @Test
    @DisplayName("field errors carry the field name and reason only")
    void fieldErrors() {
        when(service.search(anyLong(), any(), any()))
                .thenReturn(new ReadRejected(
                        ReadRejection.VALIDATION_FAILED,
                        List.of(new BusinessDataFieldError("x'; --", BusinessDataFieldErrorReason.NOT_ALLOWED)),
                        false));

        assertThatThrownBy(() -> controller.search(new SearchRequest(
                        "sample",
                        "item",
                        null,
                        List.of(new SearchConditionRequest("x'; --", "ペン", null, null, null)),
                        null,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(20))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getProperties())
                            .containsEntry("fieldErrors", List.of(Map.of("field", "x'; --", "reason", "NOT_ALLOWED")))
                            .containsEntry("definitionChanged", false);
                    assertThat(e.getProperties().toString()).doesNotContain("ペン");
                });
    }

    @Test
    @DisplayName("a field error for a name outside the DSL has an empty field and carries no name")
    void emptyFieldForUnknownNames() {
        String longName = "n".repeat(60_000);
        when(service.search(anyLong(), any(), any()))
                .thenReturn(new ReadRejected(
                        ReadRejection.VALIDATION_FAILED,
                        List.of(new BusinessDataFieldError("", BusinessDataFieldErrorReason.NOT_ALLOWED)),
                        false));

        assertThatThrownBy(() -> controller.search(new SearchRequest(
                        "sample",
                        "item",
                        null,
                        List.of(new SearchConditionRequest(longName, "x", null, null, null)),
                        null,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(20))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getProperties())
                            .containsEntry("fieldErrors", List.of(Map.of("field", "", "reason", "NOT_ALLOWED")));
                    assertThat(e.getProperties().toString()).hasSizeLessThan(500);
                });
    }

    @Test
    @DisplayName("the page and the detail carry keys, values and truncated columns")
    void pageAndDetail() {
        RowRecord row = new RowRecord(
                new RowValues(Map.of("code", "7"), Set.of()),
                new RowValues(Map.of("code", "7", "name", "ペン"), Set.of("name")));
        when(service.search(anyLong(), any(), any()))
                .thenReturn(new SearchResult.Found(VIEW, 2, 50, 51, List.of(row), false));
        when(service.get(anyLong(), any(), any())).thenReturn(new RowReadResult.Found(VIEW, row, true));

        RowPageResponse page = controller.search(
                new SearchRequest("sample", "item", null, null, new SortKeyBody("name", "DESC"), null, null));
        RowDetailResponse detail = controller.get(new RowGetRequest("sample", "item", null, Map.of("code", "7")));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(51);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.key()).containsEntry("code", "7");
            assertThat(item.truncatedColumns()).containsExactly("name");
        });
        assertThat(detail.definitionChanged()).isTrue();
        assertThat(detail.values()).containsEntry("name", "ペン");
        assertThat(page.toString()).doesNotContain("ペン");
        assertThat(detail.toString()).doesNotContain("ペン").doesNotContain("code");
        assertThat(page.items().getFirst().toString()).doesNotContain("ペン");
    }

    @Test
    @DisplayName("a row without a key is returned with a null key")
    void rowWithoutKey() {
        RowRecord row = new RowRecord(null, new RowValues(Map.of("name", "x"), Set.of()));
        when(service.search(anyLong(), any(), any()))
                .thenReturn(new SearchResult.Found(VIEW, 1, 20, 1, List.of(row), false));

        RowPageResponse page = controller.search(new SearchRequest("sample", "item", null, null, null, null, null));

        assertThat(page.items().getFirst().key()).isNull();
        assertThat(page.items().getFirst().toString()).contains("key=none");
    }

    @Test
    @DisplayName("the request records hide values in their string forms")
    void requestStrings() {
        SearchRequest search = new SearchRequest(
                "sample",
                "item",
                new DefinitionIdBody(BusinessDataDslFixture.HASH, 7L),
                List.of(new SearchConditionRequest("name", "ペン", null, null, List.of("a"))),
                null,
                null,
                null);
        RowGetRequest get = new RowGetRequest("sample", "item", null, Map.of("code", "ペン"));

        assertThat(search.toString()).doesNotContain("ペン").contains("conditions=1");
        assertThat(search.conditions().getFirst().toString()).doesNotContain("ペン");
        assertThat(get.toString()).doesNotContain("ペン").contains("keyColumns=1");
        assertThat(new DefinitionIdBody(BusinessDataDslFixture.HASH, 7L).toString())
                .doesNotContain(BusinessDataDslFixture.HASH);
        assertThat(search.toInput().conditions()).hasSize(1);
        assertThat(new RowGetRequest("s", "t", null, null).toInput().values()).isEmpty();
        assertThat(DefinitionIdBody.toRequested(null)).isNull();
    }

    @Test
    @DisplayName("the controller refuses to start without the ACCESS_DENIED type")
    void needsAccessDenied() {
        ProblemTypeRegistry empty = mock(ProblemTypeRegistry.class);
        when(empty.findByCode(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new BusinessDataReadController(
                        service, references, mock(BusinessDataRequestContext.class), empty))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BusinessDataRequestContext(empty)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the web config limits each of the three paths to 64 KB")
    void bodyLimits() {
        assertThat(BusinessDataWebConfig.businessDataDefinitionBodyLimitRoute().path())
                .isEqualTo("/api/tables/definition");
        assertThat(BusinessDataWebConfig.businessDataSearchBodyLimitRoute().path())
                .isEqualTo("/api/tables/rows/search");
        assertThat(BusinessDataWebConfig.businessDataGetBodyLimitRoute().maxBytes())
                .isEqualTo(65_536L);
    }

    @ParameterizedTest(name = "purpose \"{0}\"")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"create", "DELETE", "", " VIEW", "view"})
    @DisplayName("a purpose outside VIEW, CREATE and UPDATE is 400 before the service is called (BR1.1)")
    void unknownPurposeIs400(String purpose) {
        assertThatThrownBy(() -> controller.definition(new DefinitionRequest("sample", "item", null, purpose)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getProblemType().status()).isEqualTo(400);
                    assertThat(e.getProblemType().code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(e.getProperties()).containsOnlyKeys("definitionChanged");
                });
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.EnumSource(DefinitionPurpose.class)
    @DisplayName("each purpose is passed to the service and a missing purpose is VIEW")
    void purposeIsPassed(DefinitionPurpose purpose) {
        when(service.definition(eq(42L), any(), eq(purpose)))
                .thenReturn(new DefinitionResult.Found(VIEW, false, purpose, Map.of()));

        controller.definition(new DefinitionRequest("sample", "item", null, purpose.name()));
        if (purpose == DefinitionPurpose.VIEW) {
            controller.definition(new DefinitionRequest("sample", "item", null));
        }

        verify(service, org.mockito.Mockito.times(purpose == DefinitionPurpose.VIEW ? 2 : 1))
                .definition(eq(42L), any(), eq(purpose));
    }

    @Test
    @DisplayName("reference fields go to their columns, other columns carry reference null, and lookups say lookup")
    void referencesInTheResponse() {
        cherry.mastersmith.businessdata.domain.ReferenceField choices =
                cherry.mastersmith.businessdata.domain.ReferenceField.choices(
                        List.of(new cherry.mastersmith.businessdata.domain.ReferenceOption("1", null)));
        cherry.mastersmith.businessdata.domain.ReferenceField lookup =
                cherry.mastersmith.businessdata.domain.ReferenceField.lookup(
                        new cherry.mastersmith.businessdata.domain.PickerInfo(
                                List.of(new cherry.mastersmith.businessdata.domain.PickerSearchField(
                                        "n",
                                        new cherry.mastersmith.dsl.domain.DisplayName("名", "Name"),
                                        cherry.mastersmith.businessdata.domain.SearchMode.CONTAINS,
                                        ValueType.STRING)),
                                List.of(new cherry.mastersmith.businessdata.domain.PickerListColumn(
                                        "n",
                                        new cherry.mastersmith.dsl.domain.DisplayName("名", "Name"),
                                        ValueType.STRING))));
        when(service.definition(eq(42L), any(), eq(DefinitionPurpose.CREATE)))
                .thenReturn(new DefinitionResult.Found(
                        VIEW, false, DefinitionPurpose.CREATE, Map.of("code", choices, "name", lookup)));

        TableDefinitionResponse response =
                controller.definition(new DefinitionRequest("sample", "item", null, "CREATE"));

        ColumnDefinitionResponse code = response.listColumns().get(0);
        ColumnDefinitionResponse name = response.listColumns().get(1);
        assertThat(code.reference().kind()).isEqualTo(cherry.mastersmith.dsl.domain.OptionSourceKind.REFERENCE);
        assertThat(code.reference().options()).singleElement().satisfies(o -> {
            assertThat(o.value()).isEqualTo("1");
            assertThat(o.label()).isNull();
            assertThat(o.toString()).doesNotContain("\"1\"").contains("***");
        });
        assertThat(code.reference().picker()).isNull();
        assertThat(code.formPart()).isEqualTo("text");
        assertThat(name.reference().options()).isNull();
        assertThat(name.reference().picker().searchFields())
                .singleElement()
                .satisfies(f ->
                        assertThat(f.operator()).isEqualTo(cherry.mastersmith.businessdata.domain.SearchMode.CONTAINS));
        assertThat(name.reference().picker().listColumns())
                .singleElement()
                .satisfies(f -> assertThat(f.operator()).isNull());
        assertThat(name.formPart()).as("作成・更新では lookup のまま（D2）").isEqualTo("lookup");
        assertThat(response.detailColumns().get(2).reference()).isNull();
        assertThat(code.reference().toString()).contains("options=1").doesNotContain("value=1");
    }

    @Test
    @DisplayName("VIEW keeps the text form part and reference null on every column (BR12.1)")
    void viewKeepsTheOldShape() {
        when(service.definition(eq(42L), any(), eq(DefinitionPurpose.VIEW)))
                .thenReturn(new DefinitionResult.Found(VIEW, false));

        TableDefinitionResponse response = controller.definition(new DefinitionRequest("sample", "item", null));

        assertThat(response.listColumns()).allSatisfy(c -> {
            assertThat(c.reference()).isNull();
            assertThat(c.formPart()).isEqualTo("text");
        });
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "TARGET_DB_TIMEOUT, 503, BUSINESS_TARGET_DB_TIMEOUT",
        "TARGET_DB_UNAVAILABLE, 503, BUSINESS_TARGET_DB_UNAVAILABLE",
        "TARGET_DB_MISMATCH, 409, BUSINESS_TARGET_DB_MISMATCH"
    })
    @DisplayName("a reference read failure of the definition becomes the existing code")
    void definitionFailures(ReadRejection rejection, int status, String code) {
        when(service.definition(eq(42L), any(), eq(DefinitionPurpose.UPDATE))).thenReturn(ReadRejected.of(rejection));

        assertThatThrownBy(() -> controller.definition(new DefinitionRequest("sample", "item", null, "UPDATE")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getProblemType().status()).isEqualTo(status);
                    assertThat(e.getProblemType().code()).isEqualTo(code);
                });
    }

    // ── 参照先の検索とラベルの1件読み（Intent 261029-reference-lookup-cleanup の U1、B2） ──

    private static ReferenceRowsRequest searchBody(String column, String value, Object page) {
        return new ReferenceRowsRequest(
                "sample",
                "orders",
                "pick",
                List.of(new ReferenceConditionRequest(column, value)),
                page == null ? null : new BigDecimal(page.toString()),
                null);
    }

    private static ReferenceRowsRequest labelBody(String value) {
        return new ReferenceRowsRequest("sample", "orders", "pick", null, null, value);
    }

    @Test
    @DisplayName("a format error is 400 without field errors before the lookup is called (BR1.2 step 2 before step 3)")
    void referenceFormatErrorComesFirst() {
        List<ReferenceRowsRequest> malformed = List.of(
                new ReferenceRowsRequest(null, "orders", "pick", null, null, "1"),
                new ReferenceRowsRequest(
                        "sample",
                        "orders",
                        "pick",
                        List.of(new ReferenceConditionRequest("l", "x")),
                        BigDecimal.ONE,
                        "1"),
                new ReferenceRowsRequest("sample", "orders", "pick", null, null, null),
                searchBody("l", "", 1),
                searchBody("l", "x", 0),
                searchBody("l", "x", null),
                searchBody("l", "あ".repeat(1_001), 1),
                labelBody("1".repeat(1_001)));

        for (ReferenceRowsRequest body : malformed) {
            assertThatThrownBy(() -> controller.referenceRows(body))
                    .as(body.toString())
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getProblemType().code()).isEqualTo("VALIDATION_FAILED");
                        assertThat(e.getProperties()).isEmpty();
                    });
        }
        org.mockito.Mockito.verifyNoInteractions(references);
    }

    @Test
    @DisplayName("a search goes to search and a value goes to label with the user from the request context")
    void referenceRowsRouting() {
        ReferenceRows page =
                ReferenceRows.search(List.of(new ReferenceRow("7", null, java.util.Arrays.asList("班", null))), 2, true);
        when(references.search(eq(42L), any())).thenReturn(new ReferenceRowsResult.Found(page));
        when(references.label(eq(42L), any()))
                .thenReturn(new ReferenceRowsResult.Found(ReferenceRows.label(new ReferenceRow("7", "班", List.of()))));

        ReferenceRowsResponse searched = controller.referenceRows(searchBody("l", "班", 2));
        ReferenceRowsResponse labeled = controller.referenceRows(labelBody("7"));

        verify(references)
                .search(
                        42L,
                        ReferenceRowsQuery.search(
                                "sample",
                                "orders",
                                "pick",
                                List.of(new cherry.mastersmith.businessdata.domain.ReferenceCondition("l", "班")),
                                2));
        verify(references).label(42L, ReferenceRowsQuery.label("sample", "orders", "pick", new ReferenceValue("7")));
        assertThat(searched.page()).isEqualTo(2L);
        assertThat(searched.hasNext()).isTrue();
        assertThat(searched.rows()).singleElement().satisfies(row -> {
            assertThat(row.value()).isEqualTo("7");
            assertThat(row.label()).isNull();
            assertThat(row.cells()).containsExactly("班", null);
        });
        assertThat(labeled.page()).isNull();
        assertThat(labeled.hasNext()).isFalse();
        assertThat(labeled.rows())
                .singleElement()
                .satisfies(row -> assertThat(row.cells()).isEmpty());
    }

    @Test
    @DisplayName("Denied is the same 403 ACCESS_DENIED without field errors for search and label")
    void referenceDenied() {
        when(references.search(eq(42L), any())).thenReturn(new ReferenceRowsResult.Denied());
        when(references.label(eq(42L), any())).thenReturn(new ReferenceRowsResult.Denied());

        for (ReferenceRowsRequest body : List.of(searchBody("l", "x", 1), labelBody("1"))) {
            assertThatThrownBy(() -> controller.referenceRows(body))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getProblemType()).isSameAs(ACCESS_DENIED);
                        assertThat(e.getProperties()).isEmpty();
                    });
        }
    }

    @Test
    @DisplayName("an invalid value is 400 with the column and INVALID_VALUE only")
    void referenceInvalidValue() {
        when(references.search(eq(42L), any())).thenReturn(new ReferenceRowsResult.InvalidValue("id"));

        assertThatThrownBy(() -> controller.referenceRows(searchBody("id", "abc", 1)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getProblemType().code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(e.getProperties())
                            .containsOnlyKeys("fieldErrors")
                            .extractingByKey("fieldErrors")
                            .isEqualTo(List.of(Map.of("field", "id", "reason", "INVALID_VALUE")));
                    assertThat(e.getProperties().toString()).doesNotContain("abc");
                });
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "TIMEOUT, BUSINESS_TARGET_DB_TIMEOUT",
        "UNAVAILABLE, BUSINESS_TARGET_DB_UNAVAILABLE",
        "MISMATCH, BUSINESS_TARGET_DB_MISMATCH"
    })
    @DisplayName("each failure reason maps to the existing code")
    void referenceFailures(ReferenceFailureReason reason, String code) {
        when(references.label(eq(42L), any())).thenReturn(new ReferenceRowsResult.Failed(reason));

        assertThatThrownBy(() -> controller.referenceRows(labelBody("1")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType().code()).isEqualTo(code));
    }

    @Test
    @DisplayName("the request and response types hide values and the body limit covers the new path")
    void referenceTypesHideValues() {
        String secret = "社外秘の値";
        ReferenceRowsRequest search = searchBody("l", secret, 1);
        ReferenceRowsRequest label = labelBody(secret);
        ReferenceRowsResponse response = ReferenceRowsResponse.from(
                ReferenceRows.search(List.of(new ReferenceRow(secret, secret, List.of(secret))), 1, false));

        assertThat(List.of(
                        search.toString(),
                        label.toString(),
                        response.toString(),
                        response.rows().toString()))
                .allSatisfy(text -> assertThat(text).doesNotContain(secret));
        assertThat(search.toString()).contains("column=pick", "conditions=1");
        assertThat(BusinessDataWebConfig.businessDataReferenceRowsBodyLimitRoute()
                        .path())
                .isEqualTo("/api/tables/reference/rows");
        assertThat(BusinessDataWebConfig.businessDataReferenceRowsBodyLimitRoute()
                        .maxBytes())
                .isEqualTo(65_536L);
    }
}
