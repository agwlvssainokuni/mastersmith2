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
import cherry.mastersmith.businessdata.domain.ReferenceCheckResult;
import cherry.mastersmith.businessdata.domain.ReferenceCondition;
import cherry.mastersmith.businessdata.domain.ReferenceCountState;
import cherry.mastersmith.businessdata.domain.ReferenceFailureReason;
import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.ReferenceOption;
import cherry.mastersmith.businessdata.domain.ReferenceReadSpec;
import cherry.mastersmith.businessdata.domain.ReferenceRow;
import cherry.mastersmith.businessdata.domain.ReferenceRows;
import cherry.mastersmith.businessdata.domain.ReferenceRowsQuery;
import cherry.mastersmith.businessdata.domain.ReferenceStatus;
import cherry.mastersmith.businessdata.domain.ReferenceValue;
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
import cherry.mastersmith.targetdb.domain.TargetReadSession;
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

    /** 検索 API と確かめの口の参照元（B2）。team_pick は整数の EQUALS と文字の CONTAINS を持つ LOOKUP。 */
    private static final DslTable PICKS = table(
            "picks",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("team_pick")
                    .form(FormPart.LOOKUP)
                    .options(new OptionSource(
                            OptionSourceKind.LOOKUP,
                            List.of(),
                            "team",
                            "id",
                            null,
                            List.of(
                                    new LookupSearchItem("id", SearchOperator.EQUALS),
                                    new LookupSearchItem("label", SearchOperator.CONTAINS)),
                            List.of(new LookupListItem("label", 1), new LookupListItem("id", 2))))
                    .build(),
            column("price_ref")
                    .form(FormPart.SELECT)
                    .options(reference("prices", "amount", "name"))
                    .build());

    /** 小数の値のカラムを持つ参照先（B2 の厳密な一致の確かめ）。 */
    private static final DslTable PRICES = table(
            "prices",
            false,
            List.of("amount"),
            column("amount").dbType("numeric").build(),
            column("name").build());

    private static final DslModel DSL =
            BusinessDataDslFixture.model("sample", ORDERS, DEPT, TEAM, SHOP, HIDDEN, PICKS, PRICES);

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

    // ── B2: 検索・ラベルの1件読み・確かめの口 ──

    private static ReferenceRowsQuery searchQuery(String column, long page, ReferenceCondition... conditions) {
        return ReferenceRowsQuery.search("sample", "picks", column, List.of(conditions), page);
    }

    private static ReferenceRowsQuery labelQuery(String table, String column, String value) {
        return ReferenceRowsQuery.label("sample", table, column, new ReferenceValue(value));
    }

    /** ピッカーの行（値・ラベル・一覧の列 id）を作る。 */
    private static RowSearchResult teamRows(int size) {
        List<RowRecord> records = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("label", "班" + i);
            values.put("id", Integer.toString(i));
            records.add(new RowRecord(
                    new RowValues(Map.of("id", Integer.toString(i)), Set.of()), new RowValues(values, Set.of())));
        }
        return new RowSearchResult.Rows(records);
    }

    private static TargetReadSession session() {
        return new TargetReadSession(
                cherry.mastersmith.targetdb.domain.DatabaseProduct.POSTGRESQL,
                org.mockito.Mockito.mock(java.sql.Connection.class),
                5);
    }

    private static RowSearchResult failed(RowQueryFailureReason reason) {
        return new RowSearchResult.Failed(
                new RowQueryFailure(reason, RowQueryStage.EXECUTE, "57014", 0, "org.postgresql.util.PSQLException"));
    }

    @Test
    @DisplayName("each 403 case of the search answers Denied without borrowing a connection")
    void searchDeniedCasesReadNothing() {
        ReferenceCondition name = new ReferenceCondition("label", "班");
        List<ReferenceRowsQuery> denied = List.of(
                ReferenceRowsQuery.search("sample", "nothere", "team_pick", List.of(name), 1),
                ReferenceRowsQuery.search("other", "picks", "team_pick", List.of(name), 1),
                ReferenceRowsQuery.search("sample", "picks", "nothere", List.of(name), 1),
                ReferenceRowsQuery.search("sample", "orders", "memo", List.of(name), 1),
                ReferenceRowsQuery.search("sample", "orders", "dept_code", List.of(name), 1),
                ReferenceRowsQuery.search("sample", "orders", "hidden_ref", List.of(name), 1),
                ReferenceRowsQuery.search("sample", "orders", "ro_ref", List.of(name), 1),
                searchQuery("team_pick", 1, new ReferenceCondition("secret", "x")),
                searchQuery("team_pick", 1, name, new ReferenceCondition("Label", "")),
                searchQuery(
                        "team_pick",
                        1,
                        new ReferenceCondition("label", "x"),
                        new ReferenceCondition("nothere", "abc")));

        for (ReferenceRowsQuery query : denied) {
            assertThat(lookup.search(1L, query)).as(query.toString()).isEqualTo(new ReferenceRowsResult.Denied());
        }
        Map<PermissionTarget, PermissionValues> readOnly = new LinkedHashMap<>();
        readOnly.put(PermissionTarget.schema("sample"), new PermissionValues(MainPermission.FULL, true, true));
        readOnly.put(
                PermissionTarget.column("sample", "picks", "team_pick"),
                new PermissionValues(MainPermission.READ, null, null));
        ports.resolver.snapshot =
                PermissionSnapshot.of(new WorkRoleRef(7L, "営業"), DSL, BusinessDataDslFixture.HASH, readOnly);
        assertThat(lookup.search(1L, searchQuery("team_pick", 1, name)))
                .as("参照元のカラムが FULL でない")
                .isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(lookup.label(1L, labelQuery("picks", "team_pick", "1"))).isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(withReads()).isZero();
        assertThat(ports.calls).doesNotContain("search");
    }

    @Test
    @DisplayName("without an applied DSL the search and the label are Denied, not 404")
    void noDslIsDenied() {
        ports.resolver.snapshot = PermissionSnapshot.of(
                new WorkRoleRef(7L, "営業"),
                null,
                null,
                Map.of(PermissionTarget.schema("sample"), new PermissionValues(MainPermission.FULL, true, true)));

        assertThat(lookup.search(1L, searchQuery("team_pick", 1, new ReferenceCondition("label", "x"))))
                .isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(lookup.label(1L, labelQuery("picks", "team_pick", "1"))).isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(withReads()).isZero();
    }

    @Test
    @DisplayName("an EQUALS value that is not of the column type is 400 INVALID_VALUE, after every 403 check")
    void equalsValueTypeIs400() {
        assertThat(lookup.search(1L, searchQuery("team_pick", 1, new ReferenceCondition("id", "abc"))))
                .isEqualTo(new ReferenceRowsResult.InvalidValue("id"));
        assertThat(lookup.search(
                        1L,
                        searchQuery(
                                "team_pick",
                                1,
                                new ReferenceCondition("id", "abc"),
                                new ReferenceCondition("nothere", ""))))
                .as("名前の 403 が値の型の 400 より先")
                .isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(withReads()).isZero();
    }

    @Test
    @DisplayName("a search borrows once, runs one statement of 21 rows from the offset and returns 20 rows and hasNext")
    void searchPage() {
        ports.executor.referenceResults.add(teamRows(21));

        ReferenceRowsResult result = lookup.search(
                1L,
                searchQuery("team_pick", 3, new ReferenceCondition("label", "班"), new ReferenceCondition("id", "")));

        assertThat(withReads()).isEqualTo(1);
        assertThat(ports.calls.stream().filter("search"::equals)).hasSize(1);
        ReferenceReadSpec spec = ports.executor.references.get(0).orElseThrow();
        assertThat(spec.maxRows()).isEqualTo(21);
        assertThat(spec.offset()).isEqualTo(40);
        assertThat(spec.count()).isFalse();
        assertThat(ports.executor.conditions)
                .as("空の条件は使わない")
                .singleElement()
                .satisfies(c ->
                        assertThat(c.mode()).isEqualTo(cherry.mastersmith.businessdata.domain.SearchMode.CONTAINS));
        assertThat(result).isInstanceOfSatisfying(ReferenceRowsResult.Found.class, found -> {
            assertThat(found.rows().rows()).hasSize(20);
            assertThat(found.rows().hasNext()).isTrue();
            assertThat(found.rows().page()).isEqualTo(3L);
            ReferenceRow first = found.rows().rows().get(0);
            assertThat(first.value()).isEqualTo("1");
            assertThat(first.label()).isEqualTo("班1");
            assertThat(first.cells()).containsExactly("班1", "1");
        });

        ports.executor.referenceResults.add(teamRows(20));
        assertThat(lookup.search(1L, searchQuery("team_pick", 1, new ReferenceCondition("id", "7"))))
                .isInstanceOfSatisfying(ReferenceRowsResult.Found.class, found -> {
                    assertThat(found.rows().hasNext()).isFalse();
                    assertThat(found.rows().rows()).hasSize(20);
                });
        assertThat(ports.executor.conditions)
                .singleElement()
                .satisfies(c -> assertThat(c.value().value()).isEqualTo(7L));
        ports.executor.referenceResults.add(teamRows(0));
        assertThat(lookup.search(1L, searchQuery("team_pick", 1_000_000, new ReferenceCondition("label", "x"))))
                .isEqualTo(new ReferenceRowsResult.Found(ReferenceRows.search(List.of(), 1_000_000, false)));
    }

    @Test
    @DisplayName("a label read works for both kinds, returns the first row and is Denied when no row or no type")
    void labelRead() {
        ports.executor.referenceResults.add(rows("code", "name", "D1", "D1"));
        assertThat(lookup.label(1L, labelQuery("orders", "dept_code", "D1")))
                .isEqualTo(
                        new ReferenceRowsResult.Found(ReferenceRows.label(new ReferenceRow("D1", "ラベルD1", List.of()))));
        assertThat(ports.executor.references.get(0).orElseThrow().maxRows()).isEqualTo(1);
        assertThat(ports.executor.conditions)
                .singleElement()
                .satisfies(c -> assertThat(c.column()).isEqualTo("code"));

        ports.executor.referenceResults.add(teamRows(1));
        assertThat(lookup.label(1L, labelQuery("picks", "team_pick", "1")))
                .isInstanceOf(ReferenceRowsResult.Found.class);

        ports.executor.referenceResults.add(teamRows(0));
        assertThat(lookup.label(1L, labelQuery("picks", "team_pick", "2")))
                .as("行が無ければ 403 の同じ応答")
                .isEqualTo(new ReferenceRowsResult.Denied());

        long searches = ports.calls.stream().filter("search"::equals).count();
        assertThat(lookup.label(1L, labelQuery("picks", "team_pick", "abc")))
                .as("型として読めない値は問い合わせずに 403")
                .isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(lookup.label(1L, labelQuery("orders", "hidden_ref", "1")))
                .isEqualTo(new ReferenceRowsResult.Denied());
        assertThat(ports.calls.stream().filter("search"::equals).count()).isEqualTo(searches);
    }

    @Test
    @DisplayName("search and label failures map to the reason with exactly one warning, and an unexpected one throws")
    void searchFailures() {
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.TIMEOUT));
        try (LogEvents events = LogEvents.capture(ReferenceLookup.class)) {
            assertThat(lookup.search(1L, searchQuery("team_pick", 1, new ReferenceCondition("label", SECRET_LABEL))))
                    .isEqualTo(new ReferenceRowsResult.Failed(ReferenceFailureReason.TIMEOUT));
            assertThat(events.list())
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                            .contains("REFERENCE", "TIMEOUT", "57014")
                            .doesNotContain(SECRET_LABEL)
                            .doesNotContain("team"));
        }
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.READ_CONVERSION));
        assertThat(lookup.label(1L, labelQuery("orders", "dept_code", "D1")))
                .isEqualTo(new ReferenceRowsResult.Failed(ReferenceFailureReason.MISMATCH));
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.UNAVAILABLE;
        assertThat(lookup.label(1L, labelQuery("orders", "dept_code", "D1")))
                .isEqualTo(new ReferenceRowsResult.Failed(ReferenceFailureReason.UNAVAILABLE));
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.NOT_CONFIGURED;
        assertThat(lookup.search(1L, searchQuery("team_pick", 1, new ReferenceCondition("label", "x"))))
                .isEqualTo(new ReferenceRowsResult.Failed(ReferenceFailureReason.UNAVAILABLE));
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.DONE;
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.UNEXPECTED));
        assertThatThrownBy(() -> lookup.label(1L, labelQuery("orders", "dept_code", "D1")))
                .isInstanceOf(BusinessDataUnexpectedException.class);
    }

    @Test
    @DisplayName("check reads nothing for a non reference, an unreadable target or an untyped value")
    void checkWithoutQuery() {
        TargetReadSession session = session();

        assertThat(lookup.check(source(), "memo", new ReferenceValue("x"), session))
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(lookup.check(source(), "hidden_ref", new ReferenceValue("1"), session))
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(lookup.check(picks(), "price_ref", new ReferenceValue("abc"), session))
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(ports.calls).doesNotContain("search");
    }

    private TableAccessResolution.Resolved picks() {
        return (TableAccessResolution.Resolved) resolver.resolve(1L, new TableQuery("sample", "picks", null));
    }

    @Test
    @DisplayName("check compares the typed value strictly: case and trailing spaces differ, 1.0 equals 1")
    void checkIsStrict() {
        TargetReadSession session = session();
        ports.executor.referenceResults.add(rows("code", "name", "ABC", "abc "));
        assertThat(lookup.check(source(), "dept_code", new ReferenceValue("abc"), session))
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        ports.executor.referenceResults.add(rows("code", "name", "ABC", "abc"));
        assertThat(lookup.check(source(), "dept_code", new ReferenceValue("abc"), session))
                .isEqualTo(new ReferenceCheckResult.Found());
        ports.executor.referenceResults.add(rows("amount", "name", "1"));
        assertThat(lookup.check(picks(), "price_ref", new ReferenceValue("1.0"), session))
                .isEqualTo(new ReferenceCheckResult.Found());
        ports.executor.referenceResults.add(rows("code", "name"));
        assertThat(lookup.check(source(), "dept_pick", new ReferenceValue("D1"), session))
                .as("LOOKUP も確かめる")
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());

        assertThat(withReads()).as("自分では借りない").isZero();
        assertThat(ports.executor.sessions).allSatisfy(s -> assertThat(s).isSameAs(session));
        assertThat(ports.executor.references)
                .allSatisfy(spec -> assertThat(spec.orElseThrow().maxRows()).isEqualTo(201));
        assertThat(ports.executor.references.get(0).orElseThrow().displayColumns())
                .isEmpty();
    }

    @Test
    @DisplayName("check compares only the first 200 rows and keeps the failure value")
    void checkLimitsAndFailures() {
        TargetReadSession session = session();
        String[] values = new String[201];
        for (int i = 0; i < 200; i++) {
            values[i] = "X" + i;
        }
        values[200] = "abc";
        ports.executor.referenceResults.add(rows("code", "name", values));
        assertThat(lookup.check(source(), "dept_code", new ReferenceValue("abc"), session))
                .as("201 行目はまだあるかを知るためだけで比べない")
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());

        ports.executor.referenceResults.add(failed(RowQueryFailureReason.TIMEOUT));
        try (LogEvents events = LogEvents.capture(ReferenceLookup.class)) {
            assertThat(lookup.check(source(), "dept_code", new ReferenceValue("abc"), session))
                    .isInstanceOfSatisfying(ReferenceCheckResult.Failed.class, f -> {
                        assertThat(f.failure().reason()).isEqualTo(ReferenceFailureReason.TIMEOUT);
                        assertThat(f.failure().sqlState()).isEqualTo("57014");
                        assertThat(f.failure().budgetExceeded()).isFalse();
                    });
            assertThat(events.list()).as("check は WARN を出さない（写す U2 が出す）").isEmpty();
        }
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.INVALID_VALUE));
        assertThatThrownBy(() -> lookup.check(source(), "dept_code", new ReferenceValue("abc"), session))
                .isInstanceOf(BusinessDataUnexpectedException.class);
    }

    @Test
    @DisplayName("countState answers NO_PERMISSION, AVAILABLE for lookups, 200 AVAILABLE, 201 TOO_MANY and FAILED")
    void countState() {
        TargetReadSession session = session();
        assertThat(lookup.countState(source(), "memo", session)).isEqualTo(new ReferenceCountState.NoPermission());
        assertThat(lookup.countState(source(), "hidden_ref", session))
                .isEqualTo(new ReferenceCountState.NoPermission());
        assertThat(lookup.countState(source(), "dept_pick", session)).isEqualTo(new ReferenceCountState.Available());
        assertThat(ports.calls).doesNotContain("search");

        ports.executor.referenceResults.add(count("code", 200));
        assertThat(lookup.countState(source(), "dept_code", session)).isEqualTo(new ReferenceCountState.Available());
        ports.executor.referenceResults.add(count("code", 201));
        assertThat(lookup.countState(source(), "dept_code", session)).isEqualTo(new ReferenceCountState.TooMany());
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.UNAVAILABLE));
        try (LogEvents events = LogEvents.capture(ReferenceLookup.class)) {
            assertThat(lookup.countState(source(), "dept_code", session))
                    .isInstanceOfSatisfying(
                            ReferenceCountState.Failed.class,
                            f -> assertThat(f.failure().reason()).isEqualTo(ReferenceFailureReason.UNAVAILABLE));
            assertThat(events.list()).isEmpty();
        }
        assertThat(ports.executor.references)
                .allSatisfy(spec -> assertThat(spec.orElseThrow().maxRows()).isEqualTo(201));
        assertThat(ports.executor.conditions).isEmpty();
        assertThat(withReads()).isZero();
    }

    @Test
    @DisplayName("the results of the new ports print no value")
    void resultsHideValues() {
        ports.executor.referenceResults.add(rows("code", "name", SECRET_LABEL));
        ReferenceRowsResult result = lookup.label(1L, labelQuery("orders", "dept_code", SECRET_LABEL));

        assertThat(result).isInstanceOf(ReferenceRowsResult.Found.class);
        assertThat(result.toString()).doesNotContain(SECRET_LABEL).doesNotContain("ラベル");
    }
}
