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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.businessdata.domain.BusinessDataAuditReason;
import cherry.mastersmith.businessdata.domain.BusinessDataAuditResult;
import cherry.mastersmith.businessdata.domain.BusinessDataChangedEvent;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldErrorReason;
import cherry.mastersmith.businessdata.domain.KeyInput;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import cherry.mastersmith.businessdata.domain.RowWriteFailureReason;
import cherry.mastersmith.businessdata.domain.RowWriteStage;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import cherry.mastersmith.businessdata.domain.WriteRejection;
import cherry.mastersmith.businessdata.domain.WriteRequest;
import cherry.mastersmith.businessdata.domain.WriteValues;
import cherry.mastersmith.businessdata.repository.RowChangeResult;
import cherry.mastersmith.businessdata.repository.RowInsertResult;
import cherry.mastersmith.businessdata.repository.RowInspectResult;
import cherry.mastersmith.businessdata.repository.RowPrecheckResult;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.FakeBusinessDataPorts;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.targetdb.domain.TargetWriteFailure;
import cherry.mastersmith.targetdb.domain.TargetWriteStage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 業務データの書き込みの業務処理の単体テスト（Intent 261016-business-data-views の U2、BR1・BR6・BR8・BR9.4・BR10・BR11、計画の
 * D1〜D4、NFR4.14・NFR8.4）。偽の口と写しで、判定の順・口を1回だけ呼ぶこと・出来事を出す時点・ログの1か所を確かめる。値はすべて架空。
 */
class BusinessDataWriteServiceTest {

    private static final DslTable ITEM = table(
            "item",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("name")
                    .validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL)
                    .build(),
            column("note").build());

    private static final DslTable SHOW =
            table("show", true, List.of("id"), column("id").dbType("int4").build());

    private static final DslTable LOG =
            table("log", false, List.of(), column("text").build());

    private static final DslModel DSL = BusinessDataDslFixture.model("sample", ITEM, SHOW, LOG);

    private static final WorkRoleRef WORK_ROLE = new WorkRoleRef(7L, "営業");

    private static final WriteOrigin ORIGIN = new WriteOrigin("192.0.2.60", "agent", "/api/tables/rows", "trace-0060");

    private static final RequestedDefinitionId CURRENT = new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L);

    private final FakeBusinessDataPorts ports = new FakeBusinessDataPorts();

    private BusinessDataWriteService service;

    @BeforeEach
    void setUp() {
        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, true, Map.of());
        service = new BusinessDataWriteService(
                new TableAccessResolver(ports.resolver, ports.readAccess, ports.writeAccess),
                ports.writeAccess,
                ports.executor,
                ports.publisher,
                Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));
    }

    private static PermissionSnapshot snapshot(
            MainPermission main, boolean create, boolean delete, Map<String, MainPermission> columns) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema("sample"), new PermissionValues(main, create, delete));
        columns.forEach((name, value) ->
                settings.put(PermissionTarget.column("sample", "item", name), new PermissionValues(value, null, null)));
        return PermissionSnapshot.of(WORK_ROLE, DSL, BusinessDataDslFixture.HASH, settings);
    }

    private WriteResult create(String table, Map<String, String> values) {
        return service.write(
                1L,
                new WriteRequest(
                        new TableQuery("sample", table, CURRENT), WriteOperation.CREATE, null, new WriteValues(values)),
                ORIGIN);
    }

    private WriteResult update(Map<String, String> values) {
        return service.write(
                1L,
                new WriteRequest(
                        new TableQuery("sample", "item", CURRENT),
                        WriteOperation.UPDATE,
                        new KeyInput(Map.of("id", "5")),
                        new WriteValues(values)),
                ORIGIN);
    }

    private WriteResult delete() {
        return service.write(
                1L,
                new WriteRequest(
                        new TableQuery("sample", "item", CURRENT),
                        WriteOperation.DELETE,
                        new KeyInput(Map.of("id", "5")),
                        null),
                ORIGIN);
    }

    private BusinessDataChangedEvent onlyEvent() {
        assertThat(ports.events).hasSize(1);
        return (BusinessDataChangedEvent) ports.events.getFirst();
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> values = new HashMap<>();
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        }
        return values;
    }

    @Test
    @DisplayName("a create calls the lending port once, then publishes the event and returns the key")
    void createSucceeds() {
        WriteResult result = create("item", Map.of("name", "見本"));

        assertThat(result).isInstanceOfSatisfying(WriteResult.Created.class, created -> {
            assertThat(created.definitionId()).isEqualTo(BusinessDataDslFixture.DEFINITION);
            assertThat(created.key().values()).containsExactly(Map.entry("id", "101"));
        });
        assertThat(ports.calls).containsExactly("snapshotFor:1", "product", "withWrite", "precheck", "insert", "event");
        assertThat(ports.writeAccess.answers).containsExactly("Commit");
        BusinessDataChangedEvent event = onlyEvent();
        assertThat(event.result()).isEqualTo(BusinessDataAuditResult.SUCCESS);
        assertThat(event.workRoleId()).isEqualTo(7L);
        assertThat(event.detail()).contains("\"id\":\"101\"");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
    }

    @Test
    @DisplayName("rejections before the target database are returned in order without touching it")
    void earlyRejections() {
        ports.resolver.snapshot = PermissionSnapshot.of(WORK_ROLE, null, null, Map.of());
        assertThat(create("item", Map.of())).isEqualTo(WriteResult.Rejected.of(WriteRejection.DSL_NOT_APPLIED));
        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, true, Map.of());
        assertThat(create("nothing", Map.of())).isEqualTo(WriteResult.Rejected.of(WriteRejection.TABLE_NOT_FOUND));
        ports.resolver.snapshot = snapshot(MainPermission.NONE, true, true, Map.of());
        assertThat(create("item", Map.of())).isEqualTo(WriteResult.Rejected.of(WriteRejection.ACCESS_DENIED));

        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, true, Map.of());
        WriteResult stale = service.write(
                1L,
                new WriteRequest(
                        new TableQuery(
                                "sample", "item", new RequestedDefinitionId(BusinessDataDslFixture.OTHER_HASH, 7L)),
                        WriteOperation.CREATE,
                        null,
                        null),
                ORIGIN);
        WriteResult missing = service.write(
                1L,
                new WriteRequest(new TableQuery("sample", "item", null), WriteOperation.CREATE, null, null),
                ORIGIN);
        assertThat(stale).isEqualTo(WriteResult.Rejected.of(WriteRejection.DEFINITION_CHANGED));
        assertThat(missing).isEqualTo(WriteResult.Rejected.of(WriteRejection.DEFINITION_CHANGED));
        assertThat(ports.calls).doesNotContain("withWrite");
        assertThat(ports.events).as("②〜⑤ は監査に残さない").isEmpty();
    }

    @Test
    @DisplayName(
            "the primary key and view rule comes before the permission check and is audited only with the permission")
    void structuralRuleBeforePermission() {
        ports.resolver.snapshot = snapshot(MainPermission.FULL, false, false, Map.of());
        assertThat(create("show", Map.of()))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED));
        assertThat(ports.events).as("CREATE を持たない人の ⑥ は残さない").isEmpty();

        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, false, Map.of());
        assertThat(create("show", Map.of()))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED));
        assertThat(onlyEvent().reason()).isEqualTo(BusinessDataAuditReason.BUSINESS_ROW_OP_NOT_ALLOWED);

        ports.events.clear();
        WriteResult noKey = service.write(
                1L,
                new WriteRequest(
                        new TableQuery("sample", "log", CURRENT), WriteOperation.DELETE, new KeyInput(Map.of()), null),
                ORIGIN);
        assertThat(noKey).isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED));
        assertThat(ports.events).as("DELETE を持たない人の ⑥ は残さない").isEmpty();
        assertThat(ports.calls).doesNotContain("withWrite");
    }

    @Test
    @DisplayName("an update rejected by the rule is audited only for a person with a FULL column")
    void structuralRuleOfUpdate() {
        ports.resolver.snapshot = snapshot(MainPermission.READ, true, true, Map.of("id", MainPermission.NONE));
        assertThat(update(Map.of("note", "x")))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED));
        assertThat(ports.events).as("READ だけの人の ⑥ は残さない").isEmpty();

        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, true, Map.of("id", MainPermission.NONE));
        assertThat(update(Map.of("note", "x")))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED));
        assertThat(onlyEvent().detail()).as("主キーは決まっていない").isNull();
    }

    @Test
    @DisplayName("missing auxiliary permissions are 403 before the missing write setting, which is 409")
    void permissionThenWriteSetting() {
        ports.resolver.snapshot = snapshot(MainPermission.FULL, false, false, Map.of());
        ports.writeAccess.product = Optional.empty();
        assertThat(create("item", Map.of())).isEqualTo(WriteResult.Rejected.of(WriteRejection.ACCESS_DENIED));
        assertThat(delete()).isEqualTo(WriteResult.Rejected.of(WriteRejection.ACCESS_DENIED));
        ports.resolver.snapshot = snapshot(MainPermission.READ, true, true, Map.of());
        assertThat(update(Map.of("note", "x"))).isEqualTo(WriteResult.Rejected.of(WriteRejection.ACCESS_DENIED));

        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, true, Map.of());
        assertThat(create("item", Map.of())).isEqualTo(WriteResult.Rejected.of(WriteRejection.WRITE_NOT_CONFIGURED));
        assertThat(ports.calls).doesNotContain("withWrite");
        assertThat(ports.events).isEmpty();
    }

    @Test
    @DisplayName("input errors are 400 with field errors and the target database is not touched")
    void validationFailed() {
        WriteResult result = create("item", Map.of("nothing", "x"));

        assertThat(result)
                .isEqualTo(new WriteResult.Rejected(
                        WriteRejection.VALIDATION_FAILED,
                        List.of(new BusinessDataFieldError("", BusinessDataFieldErrorReason.NOT_ALLOWED))));
        assertThat(ports.calls).doesNotContain("withWrite");
        assertThat(ports.events).isEmpty();
    }

    @Test
    @DisplayName("an update or delete hitting no row or two rows rolls back and is audited with the key")
    void rowCounts() {
        ports.executor.updateResult = new RowChangeResult.Changed(0);
        assertThat(update(Map.of("note", "x"))).isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_NOT_FOUND));
        assertThat(onlyEvent().reason()).isEqualTo(BusinessDataAuditReason.BUSINESS_ROW_NOT_FOUND);
        assertThat(onlyEvent().detail()).contains("\"id\":\"5\"");

        ports.events.clear();
        ports.executor.deleteResult = new RowChangeResult.Changed(2);
        assertThat(delete()).isEqualTo(WriteResult.Rejected.of(WriteRejection.ROW_NOT_UNIQUE));
        assertThat(ports.writeAccess.answers).containsExactly("Rollback", "Rollback");

        ports.events.clear();
        ports.executor.updateResult = new RowChangeResult.Changed(1);
        assertThat(update(Map.of("note", "x"))).isEqualTo(new WriteResult.Written(BusinessDataDslFixture.DEFINITION));
        assertThat(onlyEvent().result()).isEqualTo(BusinessDataAuditResult.SUCCESS);
        assertThat(ports.calls).as("削除は事前の問い合わせをしない").containsSubsequence("withWrite", "delete");
    }

    @Test
    @DisplayName("an overlap found before writing names the columns, rolls back and logs nothing")
    void precheckDuplicate() {
        ports.executor.precheckResult = new RowPrecheckResult.Duplicate(List.of("name"));

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            assertThat(create("item", Map.of("name", "見本")))
                    .isEqualTo(new WriteResult.Rejected(
                            WriteRejection.DUPLICATE_VALUE,
                            List.of(new BusinessDataFieldError("name", BusinessDataFieldErrorReason.DUPLICATE))));
            assertThat(logs.list()).isEmpty();
        }
        assertThat(ports.calls).doesNotContain("insert");
        assertThat(onlyEvent().reason()).isEqualTo(BusinessDataAuditReason.BUSINESS_DUPLICATE_VALUE);
        assertThat(onlyEvent().detail()).as("作成の失敗では主キーが決まらない").isNull();
    }

    @Test
    @DisplayName("a constraint violation goes back to the savepoint and inspects on the same lending")
    void violationIsInspected() {
        ports.executor.insertResult = new RowInsertResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.REFERENCE_NOT_FOUND, RowWriteStage.WRITE, "23503", 0, "E"));
        ports.executor.inspectResult = new RowInspectResult.Inspected(Optional.of("note"));

        assertThat(create("item", Map.of("note", "x")))
                .isEqualTo(new WriteResult.Rejected(
                        WriteRejection.REFERENCE_NOT_FOUND,
                        List.of(new BusinessDataFieldError("note", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND))));
        assertThat(ports.calls).containsSubsequence("withWrite", "insert", "inspectReference", "event");
        assertThat(ports.calls.stream().filter("withWrite"::equals))
                .as("口は1回だけ")
                .hasSize(1);
        assertThat(ports.writeAccess.answers).containsExactly("RollbackToSavepointThenInspect");

        ports.events.clear();
        ports.executor.inspectResult = new RowInspectResult.Inspected(Optional.empty());
        ports.executor.insertResult = new RowInsertResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.DUPLICATE, RowWriteStage.WRITE, "23505", 0, "E"));
        assertThat(create("item", Map.of("note", "x")))
                .isEqualTo(new WriteResult.Rejected(
                        WriteRejection.DUPLICATE_VALUE,
                        List.of(new BusinessDataFieldError("", BusinessDataFieldErrorReason.DUPLICATE))));
    }

    @Test
    @DisplayName("a failed inspection is SAVE_REJECTED, or UNAVAILABLE when the connection stopped (D4)")
    void inspectionFailures() {
        ports.executor.insertResult = new RowInsertResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.DUPLICATE, RowWriteStage.WRITE, "23505", 0, "E"));
        ports.executor.inspectResult = new RowInspectResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.REJECTED, RowWriteStage.INSPECT, "42601", 0, "E"));
        assertThat(create("item", Map.of("name", "x")))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.SAVE_REJECTED));

        ports.writeAccess.mode = FakeBusinessDataPorts.WriteMode.INSPECT_FAILS;
        ports.writeAccess.failure = new TargetWriteFailure(
                TargetWriteStage.ROLLBACK_TO_SAVEPOINT, "08006", 0, "java.sql.SQLException", true);
        assertThat(create("item", Map.of("name", "x")))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.TARGET_DB_UNAVAILABLE));
        ports.writeAccess.failure =
                new TargetWriteFailure(TargetWriteStage.ROLLBACK, "42000", 1305, "java.sql.SQLException", false);
        assertThat(create("item", Map.of("name", "x")))
                .isEqualTo(WriteResult.Rejected.of(WriteRejection.SAVE_REJECTED));
    }

    @Test
    @DisplayName("a failed commit is TIMEOUT with an UNKNOWN audit result, and a deferred constraint is SAVE_REJECTED")
    void commitFailures() {
        ports.writeAccess.mode = FakeBusinessDataPorts.WriteMode.FAILED;

        assertThat(update(Map.of("note", "x"))).isEqualTo(WriteResult.Rejected.of(WriteRejection.TARGET_DB_TIMEOUT));
        assertThat(onlyEvent().result()).isEqualTo(BusinessDataAuditResult.UNKNOWN);

        ports.events.clear();
        ports.writeAccess.failure =
                new TargetWriteFailure(TargetWriteStage.COMMIT, "23503", 0, "java.sql.SQLException", false);
        assertThat(update(Map.of("note", "x"))).isEqualTo(WriteResult.Rejected.of(WriteRejection.SAVE_REJECTED));
        assertThat(onlyEvent().result()).isEqualTo(BusinessDataAuditResult.FAILURE);
    }

    @Test
    @DisplayName("an unavailable target logs once with the decided keys only and is audited")
    void unavailableLogsOnce() {
        ports.writeAccess.mode = FakeBusinessDataPorts.WriteMode.UNAVAILABLE;

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            assertThat(delete()).isEqualTo(WriteResult.Rejected.of(WriteRejection.TARGET_DB_UNAVAILABLE));
            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(keyValues(event))
                        .containsOnlyKeys("purpose", "stage", "reason", "sqlState", "vendorCode", "exceptionClass")
                        .containsEntry("purpose", "WRITE")
                        .containsEntry("stage", RowWriteStage.BORROW)
                        .containsEntry("reason", "UNAVAILABLE");
            });
        }
        assertThat(onlyEvent().reason()).isEqualTo(BusinessDataAuditReason.BUSINESS_TARGET_DB_UNAVAILABLE);
    }

    @Test
    @DisplayName("a conflict rolls back as UNAVAILABLE and logs the CONFLICT reason (D1)")
    void conflict() {
        ports.executor.updateResult = new RowChangeResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.CONFLICT, RowWriteStage.WRITE, "40P01", 0, "E"));

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            assertThat(update(Map.of("note", "x")))
                    .isEqualTo(WriteResult.Rejected.of(WriteRejection.TARGET_DB_UNAVAILABLE));
            assertThat(keyValues(logs.list().getFirst())).containsEntry("reason", "CONFLICT");
        }
        assertThat(ports.writeAccess.answers).containsExactly("Rollback");
    }

    @Test
    @DisplayName("a NOT NULL violation of a create is NOT_EDITABLE_NOT_NULL without a column")
    void notNull() {
        ports.executor.insertResult = new RowInsertResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.NOT_NULL, RowWriteStage.WRITE, "23502", 0, "E"));

        assertThat(create("item", Map.of()))
                .isEqualTo(new WriteResult.Rejected(
                        WriteRejection.SAVE_REJECTED,
                        List.of(new BusinessDataFieldError("", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL))));
    }

    @Test
    @DisplayName("unexpected failures throw without a message and are not audited")
    void unexpectedFailures() {
        ports.executor.insertResult = new RowInsertResult.Failed(
                new RowWriteFailure(RowWriteFailureReason.UNEXPECTED, RowWriteStage.READ_RESULT, null, null, "E"));
        assertThatThrownBy(() -> create("item", Map.of()))
                .isInstanceOf(BusinessDataUnexpectedException.class)
                .hasMessage(null);

        ports.writeAccess.mode = FakeBusinessDataPorts.WriteMode.FAILED;
        ports.writeAccess.failure =
                new TargetWriteFailure(TargetWriteStage.WORK, null, null, "NumberFormatException", false);
        assertThatThrownBy(() -> create("item", Map.of())).isInstanceOf(BusinessDataUnexpectedException.class);
        assertThat(ports.events).isEmpty();
    }

    @Test
    @DisplayName("the created key is not returned when the detail is not allowed")
    void createdKeyHiddenWithoutDetail() {
        ports.resolver.snapshot = snapshot(MainPermission.FULL, true, true, Map.of("id", MainPermission.NONE));
        ports.executor.insertResult = new RowInsertResult.Inserted(new RowValues(Map.of("id", "9"), Set.of()));

        WriteResult result = create("item", Map.of("note", "x"));

        assertThat(((WriteResult.Created) result).key().values()).isEmpty();
        assertThat(onlyEvent().detail()).as("監査には主キーを残す").contains("\"9\"");
    }
}
