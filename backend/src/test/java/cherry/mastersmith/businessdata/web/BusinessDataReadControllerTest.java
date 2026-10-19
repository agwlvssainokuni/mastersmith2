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
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.businessdata.service.BusinessDataReadService;
import cherry.mastersmith.businessdata.service.DefinitionResult;
import cherry.mastersmith.businessdata.service.ReadRejected;
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

    private BusinessDataReadController controller;

    @BeforeEach
    void setUp() {
        service = mock(BusinessDataReadService.class);
        BusinessDataRequestContext context = mock(BusinessDataRequestContext.class);
        when(context.currentUserId()).thenReturn(42L);
        ProblemTypeRegistry registry = mock(ProblemTypeRegistry.class);
        when(registry.findByCode("ACCESS_DENIED")).thenReturn(Optional.of(ACCESS_DENIED));
        controller = new BusinessDataReadController(service, context, registry);
    }

    @Test
    @DisplayName("the definition carries the read items and the validation items of every column (U2, BR14.1)")
    void definition() {
        when(service.definition(eq(42L), any())).thenReturn(new DefinitionResult.Found(VIEW, true));

        TableDefinitionResponse response = controller.definition(
                new DefinitionRequest("sample", "item", new DefinitionIdBody(BusinessDataDslFixture.HASH, 7L)));

        verify(service)
                .definition(
                        42L,
                        new TableQuery("sample", "item", new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L)));
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

        assertThatThrownBy(() -> new BusinessDataReadController(service, mock(BusinessDataRequestContext.class), empty))
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
}
