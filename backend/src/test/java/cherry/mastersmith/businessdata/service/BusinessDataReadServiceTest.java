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
package cherry.mastersmith.businessdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldErrorReason;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.KeyInput;
import cherry.mastersmith.businessdata.domain.RawCondition;
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.SearchInput;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.repository.RowDetailResult;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.FakeBusinessDataPorts;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 判定の順と流れ（BR1.1・BR1.8・BR3.1・BR5.6・BR8.3・BR8.5・BR8.6・BR9.1・BR9.2、NFR8.2、D1: A）の単体テスト。 */
class BusinessDataReadServiceTest {

    private static final String SECRET_VALUE = "ペン-090-0000-0000";

    private static final RequestedDefinitionId CURRENT = new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L);

    private static final RequestedDefinitionId OLD = new RequestedDefinitionId(BusinessDataDslFixture.OTHER_HASH, 7L);

    private FakeBusinessDataPorts ports;

    private BusinessDataReadService service;

    @BeforeEach
    void setUp() {
        ports = new FakeBusinessDataPorts();
        ports.resolver.snapshot = BusinessDataServiceFixture.snapshot();
        TableAccessResolver resolver = new TableAccessResolver(ports.resolver, ports.readAccess, ports.writeAccess);
        service = new BusinessDataReadService(
                resolver,
                ports.readAccess,
                ports.executor,
                new ReferenceLookup(resolver, ports.readAccess, ports.executor, java.time.Clock.systemUTC()));
    }

    private static SearchInput validSearch() {
        return new SearchInput(List.of(new RawCondition("name", SECRET_VALUE, null, null, null)), null, null, null);
    }

    private static SearchInput invalidSearch() {
        return new SearchInput(List.of(new RawCondition("code", "x", null, null, null)), null, null, null);
    }

    private static ReadRejection rejectionOf(Object result) {
        assertThat(result).isInstanceOf(ReadRejected.class);
        return ((ReadRejected) result).rejection();
    }

    @ParameterizedTest(name = "{0}.{1} dsl={2} -> {3}")
    @CsvSource({
        "sample, item, false, DSL_NOT_APPLIED",
        "sample, nowhere, true, TABLE_NOT_FOUND",
        "sample, secret, true, ACCESS_DENIED",
        "sample, item, true, VALIDATION_FAILED"
    })
    @DisplayName("the first rejection in the decided order wins and the target database is not touched")
    void judgementOrder(String schema, String table, boolean dsl, ReadRejection expected) {
        if (!dsl) {
            ports.resolver.snapshot = PermissionSnapshot.of(BusinessDataServiceFixture.WORK_ROLE, null, null, Map.of());
        }

        SearchResult result = service.search(1L, new TableQuery(schema, table, null), invalidSearch());

        assertThat(rejectionOf(result)).isEqualTo(expected);
        assertThat(ports.calls).doesNotContain("withRead:LIST", "search");
    }

    @Test
    @DisplayName("the detail of a table without a primary key is refused before the key is checked")
    void detailNotAllowedBeforeTheKey() {
        RowReadResult result =
                service.get(1L, new TableQuery("sample", "log", null), new KeyInput(Map.of("nonsense", "x")));

        assertThat(rejectionOf(result)).isEqualTo(ReadRejection.ROW_OPERATION_NOT_ALLOWED);
        assertThat(ports.calls).doesNotContain("withRead:DETAIL");
    }

    @Test
    @DisplayName("the snapshot is taken once and before the target database is read")
    void snapshotOnceAndFirst() {
        ports.executor.searchResult = new RowSearchResult.Page(3L, List.of());

        SearchResult result = service.search(9L, new TableQuery("sample", "item", null), validSearch());

        assertThat(ports.calls).containsExactly("snapshotFor:9", "product", "withRead:LIST", "search");
        assertThat(result).isInstanceOfSatisfying(SearchResult.Found.class, found -> {
            assertThat(found.total()).isEqualTo(3L);
            assertThat(found.page()).isEqualTo(1L);
            assertThat(found.size()).isEqualTo(20);
            assertThat(found.definitionChanged()).isFalse();
        });
        assertThat(ports.executor.conditions)
                .singleElement()
                .satisfies(c -> assertThat(c.column()).isEqualTo("name"));
    }

    @Test
    @DisplayName("definitionChanged is set on success and on 400 only")
    void definitionChanged() {
        assertThat(((DefinitionResult.Found)
                                service.definition(1L, new TableQuery("sample", "item", OLD), DefinitionPurpose.VIEW))
                        .definitionChanged())
                .isTrue();
        assertThat(((DefinitionResult.Found) service.definition(
                                1L, new TableQuery("sample", "item", CURRENT), DefinitionPurpose.VIEW))
                        .definitionChanged())
                .isFalse();
        ReadRejected invalid =
                (ReadRejected) service.search(1L, new TableQuery("sample", "item", OLD), invalidSearch());
        assertThat(invalid.definitionChanged()).isTrue();
        assertThat(invalid.fieldErrors())
                .containsExactly(new BusinessDataFieldError("code", BusinessDataFieldErrorReason.INVALID_VALUE));
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.UNAVAILABLE;
        ReadRejected unavailable =
                (ReadRejected) service.search(1L, new TableQuery("sample", "item", OLD), validSearch());
        assertThat(unavailable.definitionChanged()).isFalse();
        assertThat(ports.calls)
                .as("定義は対象DB を読まない")
                .filteredOn(c -> c.startsWith("withRead"))
                .hasSize(1);
    }

    @Test
    @DisplayName("not configured and unavailable are 503 UNAVAILABLE with one warning of keys only")
    void unavailable() {
        for (FakeBusinessDataPorts.Mode mode :
                List.of(FakeBusinessDataPorts.Mode.NOT_CONFIGURED, FakeBusinessDataPorts.Mode.UNAVAILABLE)) {
            ports.readAccess.mode = mode;
            try (LogEvents events = LogEvents.capture(BusinessDataReadService.class)) {
                assertThat(rejectionOf(service.search(1L, new TableQuery("sample", "item", null), validSearch())))
                        .isEqualTo(ReadRejection.TARGET_DB_UNAVAILABLE);
                assertWarn(
                        events.list(),
                        mode == FakeBusinessDataPorts.Mode.UNAVAILABLE ? "UNAVAILABLE" : "NOT_CONFIGURED");
            }
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "TIMEOUT, TARGET_DB_TIMEOUT",
        "UNAVAILABLE, TARGET_DB_UNAVAILABLE",
        "MISMATCH, TARGET_DB_MISMATCH",
        "READ_CONVERSION, TARGET_DB_MISMATCH",
        "INVALID_VALUE, VALIDATION_FAILED"
    })
    @DisplayName("failures of the row SQL are turned into their codes with one warning of keys only")
    void failures(RowQueryFailureReason reason, ReadRejection expected) {
        ports.executor.searchResult = new RowSearchResult.Failed(
                new RowQueryFailure(reason, RowQueryStage.EXECUTE, "57014", 0, "org.postgresql.util.PSQLException"));

        try (LogEvents events = LogEvents.capture(BusinessDataReadService.class)) {
            ReadRejected result =
                    (ReadRejected) service.search(1L, new TableQuery("sample", "item", OLD), validSearch());

            assertThat(result.rejection()).isEqualTo(expected);
            assertThat(result.fieldErrors()).isEmpty();
            assertThat(result.definitionChanged()).isEqualTo(expected == ReadRejection.VALIDATION_FAILED);
            assertWarn(events.list(), reason.name());
        }
    }

    @Test
    @DisplayName("an unexpected failure is thrown without message or cause after one warning")
    void unexpected() {
        ports.executor.detailResult = new RowDetailResult.Failed(new RowQueryFailure(
                RowQueryFailureReason.UNEXPECTED, RowQueryStage.EXECUTE, "42000", 1064, "java.sql.SQLException"));

        try (LogEvents events = LogEvents.capture(BusinessDataReadService.class)) {
            assertThatThrownBy(() ->
                            service.get(1L, new TableQuery("sample", "item", null), new KeyInput(Map.of("code", "1"))))
                    .isInstanceOfSatisfying(BusinessDataUnexpectedException.class, e -> {
                        assertThat(e.getMessage()).isNull();
                        assertThat(e.getCause()).isNull();
                    });
            assertWarn(events.list(), "UNEXPECTED");
        }
    }

    @Test
    @DisplayName("the detail tells found, not found and not unique apart and reads with the detail purpose")
    void detailOutcomes() {
        TableQuery query = new TableQuery("sample", "item", null);
        KeyInput key = new KeyInput(Map.of("code", "12"));
        RowRecord row = new RowRecord(
                new RowValues(Map.of("code", "12"), Set.of()), new RowValues(Map.of("name", SECRET_VALUE), Set.of()));

        assertThat(rejectionOf(service.get(1L, query, key))).isEqualTo(ReadRejection.ROW_NOT_FOUND);
        ports.executor.detailResult = new RowDetailResult.NotUnique();
        assertThat(rejectionOf(service.get(1L, query, key))).isEqualTo(ReadRejection.ROW_NOT_UNIQUE);
        ports.executor.detailResult = new RowDetailResult.Found(row);
        assertThat(service.get(1L, query, key))
                .isEqualTo(new RowReadResult.Found(
                        ((RowReadResult.Found) service.get(1L, query, key)).view(), row, false));
        assertThat(ports.calls).contains("withRead:DETAIL", "detail");
        assertThat(rejectionOf(service.get(1L, query, new KeyInput(Map.of("code", "x")))))
                .isEqualTo(ReadRejection.VALIDATION_FAILED);
    }

    private static void assertWarn(List<ILoggingEvent> logs, String reason) {
        assertThat(logs).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            Map<String, Object> pairs = event.getKeyValuePairs().stream()
                    .collect(Collectors.toMap(p -> p.key, p -> p.value == null ? "null" : p.value));
            assertThat(pairs).containsOnlyKeys("purpose", "reason", "sqlState", "vendorCode", "exceptionClass");
            assertThat(pairs).containsEntry("reason", reason);
            assertThat(event.getFormattedMessage() + pairs)
                    .doesNotContain(SECRET_VALUE)
                    .doesNotContain("item");
        });
    }
}
