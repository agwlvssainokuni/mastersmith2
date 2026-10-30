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
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.ReferenceFailureReason;
import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.ReferenceOption;
import cherry.mastersmith.businessdata.domain.ReferenceReadSpec;
import cherry.mastersmith.businessdata.domain.ReferenceStatus;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.FakeBusinessDataPorts;
import cherry.mastersmith.businessdata.testsupport.SteppingClock;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.WorkRoleRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 他テーブル参照の定義の取得の流れの単体テスト（Intent 261029-reference-lookup-cleanup の U1、BR3.3・BR4.1〜BR4.7・BR8.2・BR8.4・
 * BR8.5・BR8.7、T-T3）。対象DB は手で書いた偽物、時計は注入した {@link SteppingClock}。
 */
class ReferenceLookupTest {

    private static final String SECRET_LABEL = "社外秘の部署名";

    private static OptionSource reference(String table, String value, String label) {
        return new OptionSource(OptionSourceKind.REFERENCE, List.of(), table, value, label, List.of(), List.of());
    }

    /** 参照元（orders）と参照先（dept・team・shop・hidden）。 */
    private static final DslTable ORDERS = table(
            "orders",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("dept_code")
                    .form(FormPart.SELECT)
                    .options(reference("dept", "code", "name"))
                    .build(),
            column("dept_alt")
                    .form(FormPart.SELECT)
                    .options(reference("dept", "code", "name"))
                    .build(),
            column("team_id")
                    .form(FormPart.SELECT)
                    .options(reference("team", "id", "label"))
                    .build(),
            column("shop_id")
                    .form(FormPart.SELECT)
                    .options(reference("shop", "id", "name"))
                    .build(),
            column("dept_pick")
                    .form(FormPart.LOOKUP)
                    .options(new OptionSource(
                            OptionSourceKind.LOOKUP,
                            List.of(),
                            "dept",
                            "code",
                            null,
                            List.of(new LookupSearchItem("name", SearchOperator.CONTAINS)),
                            List.of(new LookupListItem("name", 1), new LookupListItem("code", 2))))
                    .build(),
            column("hidden_ref")
                    .form(FormPart.SELECT)
                    .options(reference("hidden", "id", "id"))
                    .build(),
            column("ro_ref")
                    .form(FormPart.SELECT)
                    .options(reference("dept", "code", "name"))
                    .build(),
            column("memo").build());

    private static final DslTable DEPT = table(
            "dept",
            false,
            List.of("code"),
            column("code").build(),
            column("name").build());

    private static final DslTable TEAM = table(
            "team",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("label").build());

    private static final DslTable SHOP = table(
            "shop", false, List.of("id"), column("id").build(), column("name").build());

    private static final DslTable HIDDEN =
            table("hidden", false, List.of("id"), column("id").build());

    private static final DslModel DSL = BusinessDataDslFixture.model("sample", ORDERS, DEPT, TEAM, SHOP, HIDDEN);

    private FakeBusinessDataPorts ports;

    private SteppingClock clock;

    private ReferenceLookup lookup;

    private TableAccessResolver resolver;

    @BeforeEach
    void setUp() {
        ports = new FakeBusinessDataPorts();
        ports.resolver.snapshot = snapshot();
        clock = new SteppingClock(1_800_000_000_000L, 0);
        resolver = new TableAccessResolver(ports.resolver, ports.readAccess, ports.writeAccess);
        lookup = new ReferenceLookup(resolver, ports.readAccess, ports.executor, clock);
    }

    private static PermissionSnapshot snapshot() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema("sample"), new PermissionValues(MainPermission.FULL, true, true));
        settings.put(PermissionTarget.table("sample", "hidden"), new PermissionValues(MainPermission.NONE, null, null));
        settings.put(
                PermissionTarget.column("sample", "orders", "ro_ref"),
                new PermissionValues(MainPermission.READ, null, null));
        return PermissionSnapshot.of(new WorkRoleRef(7L, "営業"), DSL, BusinessDataDslFixture.HASH, settings);
    }

    private TableAccessResolution.Resolved source() {
        return (TableAccessResolution.Resolved) resolver.resolve(1L, new TableQuery("sample", "orders", null));
    }

    private static RowSearchResult rows(String valueColumn, String labelColumn, String... values) {
        List<RowRecord> records = new ArrayList<>();
        for (String value : values) {
            Map<String, String> label = new LinkedHashMap<>();
            label.put(labelColumn, value == null ? null : "ラベル" + value);
            records.add(
                    new RowRecord(new RowValues(Map.of(valueColumn, value), Set.of()), new RowValues(label, Set.of())));
        }
        return new RowSearchResult.Rows(records);
    }

    private static RowSearchResult count(String valueColumn, int size) {
        return rows(
                valueColumn,
                "x",
                IntStream.range(0, size).mapToObj(Integer::toString).toArray(String[]::new));
    }

    private long withReads() {
        return ports.calls.stream().filter(c -> c.equals("withRead:REFERENCE")).count();
    }

    @Test
    @DisplayName("VIEW reads nothing and makes no reference field")
    void viewReadsNothing() {
        ReferenceLookup.DefinitionReferences result = lookup.definitionReferences(source(), DefinitionPurpose.VIEW);

        assertThat(result).isEqualTo(new ReferenceLookup.DefinitionReferences.Read(Map.of()));
        assertThat(withReads()).isZero();
        assertThat(ports.calls).doesNotContain("search");
    }

    @Test
    @DisplayName("CREATE borrows once, reads each target once and fills every reference column in the DSL order")
    void createReadsEachTargetOnce() {
        ports.executor.referenceResults.add(rows("code", "name", "D1", "D2"));
        ports.executor.referenceResults.add(rows("id", "label", "7"));
        ports.executor.referenceResults.add(rows("id", "name"));

        ReferenceLookup.DefinitionReferences result = lookup.definitionReferences(source(), DefinitionPurpose.CREATE);

        assertThat(withReads()).isEqualTo(1);
        assertThat(ports.executor.references)
                .as("同じ参照先 dept は1回だけ読む（BR4.7）")
                .hasSize(3)
                .allSatisfy(spec -> assertThat(spec).isPresent());
        assertThat(ports.executor.references.get(0).orElseThrow().valueColumn()).isEqualTo("code");
        assertThat(ports.executor.references.get(0).orElseThrow().maxRows()).isEqualTo(201);
        Map<String, ReferenceField> fields = ((ReferenceLookup.DefinitionReferences.Read) result).fields();
        assertThat(fields.keySet())
                .containsExactly("dept_code", "dept_alt", "team_id", "shop_id", "dept_pick", "hidden_ref", "ro_ref");
        assertThat(fields.get("dept_code").options())
                .extracting(ReferenceOption::value, ReferenceOption::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("D1", "ラベルD1"),
                        org.assertj.core.groups.Tuple.tuple("D2", "ラベルD2"));
        assertThat(fields.get("dept_alt")).isEqualTo(fields.get("dept_code"));
        assertThat(fields.get("team_id").options())
                .extracting(ReferenceOption::value)
                .containsExactly("7");
        assertThat(fields.get("shop_id").status()).as("空の参照先は AVAILABLE").isEqualTo(ReferenceStatus.AVAILABLE);
        assertThat(fields.get("shop_id").options()).isEmpty();
        assertThat(fields.get("dept_pick").picker()).isNotNull();
        assertThat(fields.get("dept_pick").kind()).isEqualTo(OptionSourceKind.LOOKUP);
        assertThat(fields.get("hidden_ref").status()).isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThat(fields.get("ro_ref").status())
                .as("入力できないカラムは読まず NO_PERMISSION（BR3.3）")
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThat(ports.calls.stream().filter(c -> c.startsWith("snapshotFor")).count())
                .as("写しは1回だけ")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("without a readable REFERENCE no connection is borrowed and a lookup reads nothing")
    void noReferenceNoBorrow() {
        ports.resolver.snapshot = PermissionSnapshot.of(
                new WorkRoleRef(7L, "営業"),
                DSL,
                BusinessDataDslFixture.HASH,
                Map.of(
                        PermissionTarget.schema("sample"),
                        new PermissionValues(MainPermission.FULL, true, true),
                        PermissionTarget.column("sample", "dept", "name"),
                        new PermissionValues(MainPermission.FULL, null, null),
                        PermissionTarget.table("sample", "team"),
                        new PermissionValues(MainPermission.NONE, null, null),
                        PermissionTarget.table("sample", "shop"),
                        new PermissionValues(MainPermission.NONE, null, null),
                        PermissionTarget.column("sample", "dept", "code"),
                        new PermissionValues(MainPermission.NONE, null, null),
                        PermissionTarget.table("sample", "hidden"),
                        new PermissionValues(MainPermission.NONE, null, null)));

        Map<String, ReferenceField> fields = ((ReferenceLookup.DefinitionReferences.Read)
                        lookup.definitionReferences(source(), DefinitionPurpose.UPDATE))
                .fields();

        assertThat(withReads()).isZero();
        assertThat(fields.values()).allSatisfy(f -> assertThat(f.status()).isEqualTo(ReferenceStatus.NO_PERMISSION));
    }

    @Test
    @DisplayName("200 rows are AVAILABLE, 201 rows are TOO_MANY and equal values keep only the first label")
    void boundariesAndDuplicates() {
        ports.executor.referenceResults.add(count("code", 200));
        ports.executor.referenceResults.add(count("id", 201));
        ports.executor.referenceResults.add(rows("id", "name", "A", "a", "A", "A "));

        Map<String, ReferenceField> fields = ((ReferenceLookup.DefinitionReferences.Read)
                        lookup.definitionReferences(source(), DefinitionPurpose.CREATE))
                .fields();

        assertThat(fields.get("dept_code").options()).hasSize(200);
        assertThat(fields.get("team_id")).isEqualTo(ReferenceField.tooMany());
        assertThat(fields.get("shop_id").options())
                .extracting(ReferenceOption::value)
                .containsExactly("A", "a", "A ");
        assertThat(ports.executor.references.get(0).orElseThrow().maxRows()).isEqualTo(ReferenceReadSpec.OPTION_ROWS);
    }

    @Test
    @DisplayName("a failure stops the remaining reads and logs exactly one warning without names or values")
    void failureStopsTheRest() {
        ports.executor.referenceResults.add(new RowSearchResult.Failed(new RowQueryFailure(
                RowQueryFailureReason.TIMEOUT,
                RowQueryStage.EXECUTE,
                "57014",
                0,
                "org.postgresql.util.PSQLException")));

        try (LogEvents events = LogEvents.capture(ReferenceLookup.class)) {
            ReferenceLookup.DefinitionReferences result =
                    lookup.definitionReferences(source(), DefinitionPurpose.CREATE);

            assertThat(result)
                    .isEqualTo(new ReferenceLookup.DefinitionReferences.Failed(ReferenceFailureReason.TIMEOUT));
            assertThat(ports.executor.references).as("残りを読まない").hasSize(1);
            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getKeyValuePairs())
                        .extracting(p -> p.key)
                        .containsExactly(
                                "purpose", "reason", "sqlState", "vendorCode", "exceptionClass", "budgetExceeded");
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                        .doesNotContain("dept")
                        .doesNotContain("orders")
                        .doesNotContain(SECRET_LABEL)
                        .contains("REFERENCE", "TIMEOUT", "57014", "budgetExceeded=\"false\"");
            });
        }
    }

    @Test
    @DisplayName("T-T3: when the deadline passes before the third target it is not read and the result is TIMEOUT")
    void deadlineStopsTheThirdRead() {
        ports.executor.referenceResults.add(rows("code", "name", "D1"));
        ports.executor.referenceResults.add(rows("id", "label", "7"));
        ports.executor.onReference = () -> clock.advance(4_600);

        try (LogEvents events = LogEvents.capture(ReferenceLookup.class)) {
            ReferenceLookup.DefinitionReferences result =
                    lookup.definitionReferences(source(), DefinitionPurpose.CREATE);

            assertThat(result)
                    .isEqualTo(new ReferenceLookup.DefinitionReferences.Failed(ReferenceFailureReason.TIMEOUT));
            assertThat(ports.executor.references).as("3つ目を流さない").hasSize(2);
            assertThat(ports.executor.sessions)
                    .extracting(s -> s.queryTimeoutSeconds())
                    .as("1つ目は残り 10 秒で上限 5、2つ目は残り 5.4 秒で上限 5")
                    .containsExactly(5, 5);
            assertThat(events.list())
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                            .contains("budgetExceeded=\"true\"")
                            .doesNotContain("sqlState"));
        }
    }

    @Test
    @DisplayName("the statement limit is narrowed to the rounded up remaining time")
    void narrowsTheStatementLimit() {
        ports.readAccess.referenceReadTotal = java.time.Duration.ofMillis(2_300);
        ports.executor.referenceResults.add(rows("code", "name", "D1"));
        ports.executor.referenceResults.add(rows("id", "label", "7"));
        ports.executor.referenceResults.add(rows("id", "name", "S"));

        lookup.definitionReferences(source(), DefinitionPurpose.CREATE);

        assertThat(ports.executor.sessions)
                .extracting(s -> s.queryTimeoutSeconds())
                .containsExactly(3, 3, 3);
    }

    @Test
    @DisplayName("an unconfigured or unavailable target database is UNAVAILABLE for the whole definition")
    void unavailable() {
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.NOT_CONFIGURED;
        assertThat(lookup.definitionReferences(source(), DefinitionPurpose.CREATE))
                .isEqualTo(new ReferenceLookup.DefinitionReferences.Failed(ReferenceFailureReason.UNAVAILABLE));

        ports.readAccess.mode = FakeBusinessDataPorts.Mode.UNAVAILABLE;
        try (LogEvents events = LogEvents.capture(ReferenceLookup.class)) {
            assertThat(lookup.definitionReferences(source(), DefinitionPurpose.UPDATE))
                    .isEqualTo(new ReferenceLookup.DefinitionReferences.Failed(ReferenceFailureReason.UNAVAILABLE));
            assertThat(events.list()).hasSize(1);
        }
        assertThat(ports.calls).doesNotContain("search");
    }

    @Test
    @DisplayName("a mismatch maps to MISMATCH and an unexpected failure is the unexpected 500")
    void mismatchAndUnexpected() {
        ports.executor.referenceResults.add(new RowSearchResult.Failed(new RowQueryFailure(
                RowQueryFailureReason.READ_CONVERSION, RowQueryStage.READ, null, null, "java.time.DateTimeException")));
        assertThat(lookup.definitionReferences(source(), DefinitionPurpose.CREATE))
                .isEqualTo(new ReferenceLookup.DefinitionReferences.Failed(ReferenceFailureReason.MISMATCH));

        ports.executor.referenceResults.add(new RowSearchResult.Failed(new RowQueryFailure(
                RowQueryFailureReason.UNEXPECTED, RowQueryStage.BIND, null, null, "java.lang.IllegalStateException")));
        assertThatThrownBy(() -> lookup.definitionReferences(source(), DefinitionPurpose.CREATE))
                .isInstanceOf(BusinessDataUnexpectedException.class);
    }

    @Test
    @DisplayName("the result text holds only the number of fields")
    void resultTextHidesValues() {
        ports.executor.referenceResults.add(rows("code", "name", SECRET_LABEL));

        ReferenceLookup.DefinitionReferences result = lookup.definitionReferences(source(), DefinitionPurpose.CREATE);

        assertThat(result.toString()).doesNotContain(SECRET_LABEL).contains("fields=");
        assertThat(Optional.of(result)).isPresent();
    }

    @Test
    @DisplayName("the read service maps the reference failures to the existing codes and keeps VIEW as before")
    void readServiceMapsFailures() {
        BusinessDataReadService service =
                new BusinessDataReadService(resolver, ports.readAccess, ports.executor, lookup);
        TableQuery query = new TableQuery("sample", "orders", null);

        DefinitionResult view = service.definition(1L, query, DefinitionPurpose.VIEW);
        assertThat(view).isInstanceOfSatisfying(DefinitionResult.Found.class, found -> {
            assertThat(found.purpose()).isEqualTo(DefinitionPurpose.VIEW);
            assertThat(found.references()).isEmpty();
        });
        assertThat(withReads()).isZero();

        for (var entry : Map.of(
                        RowQueryFailureReason.TIMEOUT,
                                cherry.mastersmith.businessdata.domain.ReadRejection.TARGET_DB_TIMEOUT,
                        RowQueryFailureReason.UNAVAILABLE,
                                cherry.mastersmith.businessdata.domain.ReadRejection.TARGET_DB_UNAVAILABLE,
                        RowQueryFailureReason.MISMATCH,
                                cherry.mastersmith.businessdata.domain.ReadRejection.TARGET_DB_MISMATCH)
                .entrySet()) {
            ports.executor.referenceResults.add(new RowSearchResult.Failed(
                    new RowQueryFailure(entry.getKey(), RowQueryStage.EXECUTE, null, null, "java.sql.SQLException")));
            assertThat(service.definition(1L, query, DefinitionPurpose.CREATE))
                    .isEqualTo(ReadRejected.of(entry.getValue()));
        }

        ports.executor.referenceResults.add(rows("code", "name", "D1"));
        assertThat(service.definition(1L, query, DefinitionPurpose.UPDATE))
                .isInstanceOfSatisfying(DefinitionResult.Found.class, found -> {
                    assertThat(found.purpose()).isEqualTo(DefinitionPurpose.UPDATE);
                    assertThat(found.references()).containsKey("dept_code");
                    assertThat(found.toString()).doesNotContain("ラベルD1");
                });
    }
}
