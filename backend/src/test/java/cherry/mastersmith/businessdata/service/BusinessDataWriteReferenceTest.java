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
import cherry.mastersmith.businessdata.domain.ReferenceReadSpec;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import cherry.mastersmith.businessdata.domain.WriteRejection;
import cherry.mastersmith.businessdata.domain.WriteRequest;
import cherry.mastersmith.businessdata.domain.WriteValues;
import cherry.mastersmith.businessdata.repository.RowDetailResult;
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
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.WorkRoleRef;
import java.util.ArrayList;
import java.util.HashMap;
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
 * 作成・更新の保存前の参照の段（判定の順の ⑩）の単体テスト（Intent 261029-reference-lookup-cleanup の U2、BR1.1〜BR1.4・BR2・BR3・
 * BR4・BR5、T-T3・T-F2、NFR1.32）。対象DB は手で書いた偽物、時計は注入した {@link SteppingClock}。値はすべて架空。
 */
class BusinessDataWriteReferenceTest {

    private static final String SECRET = "社外秘の参照値";

    private static OptionSource reference(String table, String value, String label) {
        return new OptionSource(OptionSourceKind.REFERENCE, List.of(), table, value, label, List.of(), List.of());
    }

    /**
     * 参照元。dept_code・dept_alt は同じ参照先 dept を指す必須の参照項目（dept_code は REQUIRED あり）、team_id・hidden_ref は
     * NULL を許す参照、shop_pick は必須の LOOKUP、hid_req は読めない参照先 hidden を指す必須の参照項目。
     */
    private static final DslTable ORDERS = table(
            "orders",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("dept_code")
                    .form(FormPart.SELECT)
                    .options(reference("dept", "code", "name"))
                    .nullable(false)
                    .validation(ValidationType.REQUIRED, ValidationOrigin.DB)
                    .build(),
            column("dept_alt")
                    .form(FormPart.SELECT)
                    .options(reference("dept", "code", "name"))
                    .nullable(false)
                    .build(),
            column("team_id")
                    .dbType("int4")
                    .form(FormPart.SELECT)
                    .options(reference("team", "id", "label"))
                    .build(),
            column("hidden_ref")
                    .form(FormPart.SELECT)
                    .options(reference("hidden", "id", "id"))
                    .build(),
            column("memo").build());

    /** 必須の LOOKUP と、読めない参照先の必須の参照項目を持つ参照元。 */
    private static final DslTable PICKS = table(
            "picks",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("shop_pick")
                    .form(FormPart.LOOKUP)
                    .options(new OptionSource(
                            OptionSourceKind.LOOKUP,
                            List.of(),
                            "shop",
                            "id",
                            null,
                            List.of(new LookupSearchItem("name", SearchOperator.CONTAINS)),
                            List.of(new LookupListItem("name", 1))))
                    .nullable(false)
                    .build(),
            column("hid_req")
                    .form(FormPart.SELECT)
                    .options(reference("hidden", "id", "id"))
                    .nullable(false)
                    .build(),
            column("memo").build());

    /** 参照の無い参照元。 */
    private static final DslTable NOTES = table(
            "notes",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
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

    private static final DslModel DSL =
            BusinessDataDslFixture.model("sample", ORDERS, PICKS, NOTES, DEPT, TEAM, SHOP, HIDDEN);

    private static final WriteOrigin ORIGIN = new WriteOrigin("192.0.2.61", "agent", "/api/tables/rows", "trace-0061");

    private static final RequestedDefinitionId CURRENT = new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L);

    private FakeBusinessDataPorts ports;

    private SteppingClock clock;

    private BusinessDataWriteService service;

    @BeforeEach
    void setUp() {
        ports = new FakeBusinessDataPorts();
        ports.resolver.snapshot = snapshot();
        clock = new SteppingClock(1_800_000_000_000L, 0);
        TableAccessResolver resolver = new TableAccessResolver(ports.resolver, ports.readAccess, ports.writeAccess);
        service = new BusinessDataWriteService(
                resolver,
                ports.readAccess,
                ports.writeAccess,
                new ReferenceLookup(resolver, ports.readAccess, ports.executor, clock),
                ports.executor,
                ports.publisher,
                clock);
    }

    private static PermissionSnapshot snapshot() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema("sample"), new PermissionValues(MainPermission.FULL, true, true));
        settings.put(PermissionTarget.table("sample", "hidden"), new PermissionValues(MainPermission.NONE, null, null));
        return PermissionSnapshot.of(new WorkRoleRef(7L, "営業"), DSL, BusinessDataDslFixture.HASH, settings);
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
                        new TableQuery("sample", "orders", CURRENT),
                        WriteOperation.UPDATE,
                        new KeyInput(Map.of("id", "5")),
                        new WriteValues(values)),
                ORIGIN);
    }

    private static Map<String, String> body(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    /** 参照先の行（値のカラムを主キーとして持つ形）。 */
    private static RowSearchResult rows(String valueColumn, String... values) {
        List<RowRecord> records = new ArrayList<>();
        for (String value : values) {
            records.add(new RowRecord(
                    new RowValues(Map.of(valueColumn, value), Set.of()), new RowValues(Map.of(), Set.of())));
        }
        return new RowSearchResult.Rows(records);
    }

    private static RowSearchResult count(String valueColumn, int size) {
        return rows(valueColumn, IntStream.range(0, size).mapToObj(i -> "V" + i).toArray(String[]::new));
    }

    private static RowSearchResult failed(RowQueryFailureReason reason) {
        return new RowSearchResult.Failed(
                new RowQueryFailure(reason, RowQueryStage.EXECUTE, "57014", 0, "org.postgresql.util.PSQLException"));
    }

    /** 更新前の行（本文の参照のカラムの値を持つ形）。 */
    private static RowDetailResult previous(Map<String, String> values) {
        return new RowDetailResult.Found(new RowRecord(
                new RowValues(Map.of("id", "5"), Set.of()), new RowValues(new LinkedHashMap<>(values), Set.of())));
    }

    private long reads() {
        return ports.calls.stream().filter(c -> c.equals("withRead:REFERENCE")).count();
    }

    private long writes() {
        return ports.calls.stream().filter(c -> c.equals("withWrite")).count();
    }

    /** 流した参照先の読み取りの種類（出す列の無い check と、ラベルを出す countState の選択肢の読み方）。 */
    private List<String> specKinds() {
        return ports.executor.references.stream()
                .map(r -> r.orElseThrow().displayColumns().isEmpty() ? CHECK : OPTIONS)
                .toList();
    }

    private static final String CHECK = "CHECK";

    private static final String OPTIONS = "OPTIONS";

    private List<BusinessDataChangedEvent> events() {
        return ports.events.stream().map(BusinessDataChangedEvent.class::cast).toList();
    }

    private static WriteResult.Rejected rejected(WriteResult result) {
        assertThat(result).isInstanceOf(WriteResult.Rejected.class);
        return (WriteResult.Rejected) result;
    }

    private static BusinessDataFieldError error(String column, BusinessDataFieldErrorReason reason) {
        return new BusinessDataFieldError(column, reason);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> values = new HashMap<>();
        event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        return values;
    }

    @Test
    @DisplayName("an input error answers 400 before reading the target database")
    void inputErrorReadsNothing() {
        WriteResult result = create("orders", body("dept_code", "D1", "dept_alt", "D1", "team_id", "abc"));

        assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.VALIDATION_FAILED);
        assertThat(rejected(result).fieldErrors())
                .containsExactly(error("team_id", BusinessDataFieldErrorReason.INVALID_VALUE));
        assertThat(reads()).isZero();
        assertThat(writes()).isZero();
    }

    @Test
    @DisplayName("BR1.2: a create and an update without reference values borrow no read connection")
    void noReferenceNoBorrow() {
        WriteResult created = create("notes", body("id", "1", "memo", "見本"));
        WriteResult updated = update(body("memo", "見本"));
        WriteResult nullRef = update(body("team_id", null));

        assertThat(created).isInstanceOf(WriteResult.Created.class);
        assertThat(updated).isInstanceOf(WriteResult.Written.class);
        assertThat(nullRef).isInstanceOf(WriteResult.Written.class);
        assertThat(reads()).isZero();
        assertThat(ports.calls).doesNotContain("detail", "search");
    }

    @Test
    @DisplayName("a create checks every reference value in the DSL order and counts the shared target once")
    void createChecksInOrder() {
        ports.executor.referenceResults.add(count("code", 3));
        ports.executor.referenceResults.add(rows("code", "D1"));
        ports.executor.referenceResults.add(rows("code", "D2"));
        ports.executor.referenceResults.add(rows("id", "7"));

        WriteResult result = create("orders", body("dept_code", "D1", "dept_alt", "D2", "team_id", "7"));

        assertThat(result).isInstanceOf(WriteResult.Created.class);
        assertThat(reads()).isEqualTo(1);
        assertThat(specKinds())
                .as("同じ参照先 dept の countState は1回（BR4.7）、check は3つ")
                .containsExactly(OPTIONS, CHECK, CHECK, CHECK);
        assertThat(ports.calls.indexOf("withRead:REFERENCE"))
                .as("読み取りを返してから書き込みを借りる（BR4.1）")
                .isLessThan(ports.calls.indexOf("withWrite"));
        assertThat(writes()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR2.7: every rejected reference is listed, 409 is audited once with an empty detail and no warning")
    void listsEveryRejection() {
        ports.executor.referenceResults.add(count("code", 3));
        ports.executor.referenceResults.add(rows("code"));
        ports.executor.referenceResults.add(rows("code", "D2"));
        ports.executor.referenceResults.add(rows("id"));

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            WriteResult result = create("orders", body("dept_code", SECRET, "dept_alt", "D2", "team_id", "7"));

            assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.REFERENCE_NOT_FOUND);
            assertThat(rejected(result).fieldErrors())
                    .containsExactly(
                            error("dept_code", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND),
                            error("team_id", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));
            assertThat(logs.list()).as("拒否では診断の WARN を出さない").isEmpty();
        }
        assertThat(writes()).isZero();
        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.result()).isEqualTo(BusinessDataAuditResult.FAILURE);
            assertThat(event.reason()).isEqualTo(BusinessDataAuditReason.BUSINESS_REFERENCE_NOT_FOUND);
            assertThat(event.detail()).isNull();
        });
        assertThat(ports.calls.indexOf("event")).isGreaterThan(ports.calls.indexOf("withRead:REFERENCE"));
    }

    @Test
    @DisplayName("functional-spec 4.3: an unreadable required reference with a value is 409 without any check query")
    void noPermissionWithValue() {
        ports.executor.referenceResults.add(rows("id", "S1"));
        WriteResult result = create("picks", body("shop_pick", "S1", "hid_req", SECRET));

        assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.REFERENCE_NOT_FOUND);
        assertThat(rejected(result).fieldErrors())
                .containsExactly(error("hid_req", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));
        assertThat(specKinds()).as("LOOKUP の状態は問い合わせず、shop_pick の check だけ").containsExactly(CHECK);
    }

    @Test
    @DisplayName(
            "functional-spec 4.3: an unreadable or too many required reference that is empty is NOT_EDITABLE_NOT_NULL")
    void deadEndEmpty() {
        ports.executor.referenceResults.add(rows("id", "S1"));
        WriteResult noPermission = create("picks", body("shop_pick", "S1"));
        ports.executor.referenceResults.add(count("code", ReferenceReadSpec.OPTION_ROWS));
        WriteResult tooMany = create("orders", body("team_id", null));

        assertThat(rejected(noPermission).fieldErrors())
                .containsExactly(error("hid_req", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL));
        assertThat(rejected(tooMany).rejection()).isEqualTo(WriteRejection.REFERENCE_NOT_FOUND);
        assertThat(rejected(tooMany).fieldErrors())
                .containsExactly(
                        error("dept_code", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL),
                        error("dept_alt", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL));
    }

    @Test
    @DisplayName("functional-spec 4.3: a too many required reference with a value is rejected even if it exists")
    void tooManyWithValue() {
        ports.executor.referenceResults.add(count("code", ReferenceReadSpec.OPTION_ROWS));

        WriteResult result = create("orders", body("dept_code", "V1", "dept_alt", "V2"));

        assertThat(rejected(result).fieldErrors())
                .containsExactly(
                        error("dept_code", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND),
                        error("dept_alt", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));
        assertThat(specKinds()).as("check を流さない").containsExactly(OPTIONS);
    }

    @Test
    @DisplayName(
            "BR1.3: an empty available required reference returns the held 400 even if another reference is rejected")
    void heldErrorBeatsRejection() {
        ports.executor.referenceResults.add(count("code", 3));
        ports.executor.referenceResults.add(rows("code", "D2"));
        ports.executor.referenceResults.add(rows("id"));

        WriteResult result = create("orders", body("dept_alt", "D2", "team_id", "7"));

        assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.VALIDATION_FAILED);
        assertThat(rejected(result).fieldErrors())
                .containsExactly(error("dept_code", BusinessDataFieldErrorReason.REQUIRED));
        assertThat(ports.events).as("400 は監査に残さない").isEmpty();
    }

    @Test
    @DisplayName("BR3.6: with another input error an unreadable required reference error is dropped before reading")
    void otherErrorDropsUnreadableHold() {
        WriteResult result = create("picks", body("shop_pick", null, "hid_req", null, "id", "abc"));

        assertThat(rejected(result).fieldErrors())
                .as("読めない hid_req の保留した誤りを落とし、使える shop_pick の誤りは元の位置に残す")
                .containsExactly(
                        error("shop_pick", BusinessDataFieldErrorReason.NOT_NULL),
                        error("id", BusinessDataFieldErrorReason.INVALID_VALUE));
        assertThat(reads()).isZero();
    }

    @Test
    @DisplayName("BR1.2: a required lookup only borrows a read connection but runs no statement for its state")
    void requiredLookupBorrows() {
        ports.executor.referenceResults.add(count("id", 1));
        ports.executor.referenceResults.add(rows("id", "S1"));
        ports.executor.referenceResults.add(rows("id"));
        PermissionSnapshot open = PermissionSnapshot.of(
                new WorkRoleRef(7L, "営業"),
                DSL,
                BusinessDataDslFixture.HASH,
                Map.of(PermissionTarget.schema("sample"), new PermissionValues(MainPermission.FULL, true, true)));
        ports.resolver.snapshot = open;

        WriteResult result = create("picks", body("shop_pick", "S1", "hid_req", "H1"));

        assertThat(reads()).isEqualTo(1);
        assertThat(specKinds()).containsExactly(OPTIONS, CHECK, CHECK);
        assertThat(rejected(result).fieldErrors())
                .containsExactly(error("hid_req", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));
    }

    @Test
    @DisplayName("BR2.4: an update checks only the references whose value changed")
    void updateChecksOnlyChanged() {
        ports.executor.detailResult = previous(body("dept_code", "D1", "team_id", "007"));
        ports.executor.referenceResults.add(rows("code"));

        WriteResult unchanged = update(body("dept_code", "D1", "team_id", "7", "memo", "新しい"));
        assertThat(unchanged).isInstanceOf(WriteResult.Written.class);
        assertThat(specKinds()).as("値の変わらない参照は確かめない").isEmpty();

        WriteResult changed = update(body("dept_code", "D9"));
        assertThat(rejected(changed).fieldErrors())
                .containsExactly(error("dept_code", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));
        assertThat(specKinds()).containsExactly(CHECK);
        assertThat(events())
                .filteredOn(event -> event.result() == BusinessDataAuditResult.FAILURE)
                .singleElement()
                .satisfies(event ->
                        assertThat(event.detail()).contains("\"id\":\"5\"").doesNotContain("D9"));
    }

    @Test
    @DisplayName("BR2.5: a truncated or unreadable previous value is treated as changed and checked")
    void truncatedPreviousIsChecked() {
        ports.executor.detailResult = new RowDetailResult.Found(new RowRecord(
                new RowValues(Map.of("id", "5"), Set.of()),
                new RowValues(Map.of("dept_code", "D1"), Set.of("dept_code"))));
        ports.executor.referenceResults.add(rows("code", "D1"));

        WriteResult result = update(body("dept_code", "D1"));

        assertThat(result).isInstanceOf(WriteResult.Written.class);
        assertThat(specKinds()).containsExactly(CHECK);
    }

    @Test
    @DisplayName("BR2.6: a missing or duplicated previous row answers before any reference rejection")
    void missingRowFirst() {
        ports.executor.detailResult = new RowDetailResult.NotFound();
        WriteResult missing = update(body("dept_code", "D9"));
        ports.executor.detailResult = new RowDetailResult.NotUnique();
        WriteResult notUnique = update(body("dept_code", "D9"));

        assertThat(rejected(missing).rejection()).isEqualTo(WriteRejection.ROW_NOT_FOUND);
        assertThat(rejected(notUnique).rejection()).isEqualTo(WriteRejection.ROW_NOT_UNIQUE);
        assertThat(specKinds()).isEmpty();
        assertThat(writes()).isZero();
        assertThat(events())
                .extracting(BusinessDataChangedEvent::reason)
                .containsExactly(
                        BusinessDataAuditReason.BUSINESS_ROW_NOT_FOUND,
                        BusinessDataAuditReason.BUSINESS_ROW_NOT_UNIQUE);
    }

    @Test
    @DisplayName("T-F2: a failure of the second check stops the third, logs exactly one warning and saves nothing")
    void failureStopsTheRest() {
        ports.executor.referenceResults.add(count("code", 3));
        ports.executor.referenceResults.add(rows("code", "D1"));
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.TIMEOUT));

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            WriteResult result = create("orders", body("dept_code", "D1", "dept_alt", SECRET, "team_id", "7"));

            assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.TARGET_DB_TIMEOUT);
            assertThat(rejected(result).fieldErrors()).isEmpty();
            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getKeyValuePairs())
                        .extracting(p -> p.key)
                        .containsExactly(
                                "purpose",
                                "stage",
                                "reason",
                                "sqlState",
                                "vendorCode",
                                "exceptionClass",
                                "budgetExceeded");
                Map<String, Object> keys = keyValues(event);
                assertThat(keys.get("purpose")).isEqualTo("REFERENCE");
                assertThat(String.valueOf(keys.get("stage"))).isEqualTo("CHECK");
                assertThat(String.valueOf(keys.get("reason"))).isEqualTo("TIMEOUT");
                assertThat(keys.get("budgetExceeded")).isEqualTo(false);
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                        .doesNotContain(SECRET)
                        .doesNotContain("orders")
                        .doesNotContain("dept");
            });
        }
        assertThat(specKinds()).as("3つ目を流さない").hasSize(3);
        assertThat(writes()).isZero();
        assertThat(events())
                .singleElement()
                .satisfies(event ->
                        assertThat(event.reason()).isEqualTo(BusinessDataAuditReason.BUSINESS_TARGET_DB_TIMEOUT));
    }

    @Test
    @DisplayName("T-T3: when the deadline passes before the third check it is not run and the answer is 503 TIMEOUT")
    void deadlineStopsTheThirdCheck() {
        ports.executor.referenceResults.add(count("code", 3));
        ports.executor.referenceResults.add(rows("code", "D1"));
        ports.executor.referenceResults.add(rows("code", "D2"));
        ports.executor.onReference = () -> clock.advance(3_100);

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            WriteResult result = create("orders", body("dept_code", "D1", "dept_alt", "D2", "team_id", "7"));

            assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.TARGET_DB_TIMEOUT);
            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(keyValues(event).get("budgetExceeded")).isEqualTo(true);
                assertThat(keyValues(event)).doesNotContainKeys("sqlState", "vendorCode", "exceptionClass");
            });
        }
        assertThat(specKinds()).as("残り 0.7 秒で3つ目の check を流さない").hasSize(3);
        assertThat(ports.executor.sessions)
                .extracting(s -> s.queryTimeoutSeconds())
                .as("残り 10 秒・6.9 秒・3.8 秒で、上限は 5・5・4")
                .containsExactly(5, 5, 4);
        assertThat(writes()).isZero();
    }

    @Test
    @DisplayName("BR4.5: an unavailable or unconfigured read connection is 503 UNAVAILABLE at the BORROW stage")
    void borrowFailures() {
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.UNAVAILABLE;
        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            WriteResult result = create("orders", body("dept_code", "D1", "dept_alt", "D1"));

            assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.TARGET_DB_UNAVAILABLE);
            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(String.valueOf(keyValues(event).get("stage"))).isEqualTo("BORROW");
                assertThat(keyValues(event)).containsKey("sqlState");
            });
        }
        ports.readAccess.mode = FakeBusinessDataPorts.Mode.NOT_CONFIGURED;
        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            WriteResult result = update(body("dept_code", "D1"));

            assertThat(rejected(result).rejection()).isEqualTo(WriteRejection.TARGET_DB_UNAVAILABLE);
            assertThat(logs.list())
                    .singleElement()
                    .satisfies(event -> assertThat(keyValues(event).keySet())
                            .containsExactlyInAnyOrder("purpose", "stage", "reason", "budgetExceeded"));
        }
        assertThat(writes()).isZero();
        assertThat(ports.calls).doesNotContain("search", "detail");
    }

    @Test
    @DisplayName("BR1.4: a mismatch is 409 MISMATCH, and a previous row failure is mapped at the PREVIOUS_ROW stage")
    void mismatchAndPreviousRowFailure() {
        ports.executor.detailResult = previous(body("team_id", "1"));
        ports.executor.referenceResults.add(failed(RowQueryFailureReason.MISMATCH));
        WriteResult mismatch = update(body("team_id", "7"));

        try (LogEvents logs = LogEvents.capture(BusinessDataWriteService.class)) {
            ports.executor.detailResult = new RowDetailResult.Failed(new RowQueryFailure(
                    RowQueryFailureReason.UNAVAILABLE, RowQueryStage.EXECUTE, "08006", 0, "java.sql.SQLException"));
            WriteResult unavailable = update(body("team_id", "7"));

            assertThat(rejected(unavailable).rejection()).isEqualTo(WriteRejection.TARGET_DB_UNAVAILABLE);
            assertThat(logs.list())
                    .singleElement()
                    .satisfies(
                            event -> assertThat(String.valueOf(keyValues(event).get("stage")))
                                    .isEqualTo("PREVIOUS_ROW"));
        }
        assertThat(rejected(mismatch).rejection()).isEqualTo(WriteRejection.TARGET_DB_MISMATCH);
    }

    @Test
    @DisplayName("an unmapped previous row failure is the unexpected 500 without a message")
    void unexpectedPreviousRow() {
        ports.executor.detailResult = new RowDetailResult.Failed(new RowQueryFailure(
                RowQueryFailureReason.UNEXPECTED,
                RowQueryStage.EXECUTE,
                null,
                null,
                "java.lang.IllegalStateException"));

        assertThatThrownBy(() -> update(body("team_id", "7"))).isInstanceOf(BusinessDataUnexpectedException.class);
        assertThat(writes()).isZero();
    }

    @Test
    @DisplayName("a delete never reads references")
    void deleteNeverReads() {
        WriteResult result = service.write(
                1L,
                new WriteRequest(
                        new TableQuery("sample", "orders", CURRENT),
                        WriteOperation.DELETE,
                        new KeyInput(Map.of("id", "5")),
                        null),
                ORIGIN);

        assertThat(result).isInstanceOf(WriteResult.Written.class);
        assertThat(reads()).isZero();
        assertThat(Optional.ofNullable(ports.executor.values)).isEmpty();
    }
}
