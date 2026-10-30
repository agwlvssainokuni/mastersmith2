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

import cherry.mastersmith.businessdata.domain.AllowedOperation;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.DefinitionId;
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.FakeBusinessDataPorts;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 権限の写しと名前の照合（BR1.2〜BR1.4・BR3.1〜BR3.3）の単体テスト。 */
class TableAccessResolverTest {

    private FakeBusinessDataPorts ports;

    private TableAccessResolver resolver;

    @BeforeEach
    void setUp() {
        ports = new FakeBusinessDataPorts();
        ports.resolver.snapshot = BusinessDataServiceFixture.snapshot();
        resolver = new TableAccessResolver(ports.resolver, ports.readAccess, ports.writeAccess);
    }

    private TableAccessResolution resolve(String schema, String table) {
        return resolver.resolve(42L, new TableQuery(schema, table, null));
    }

    @Test
    @DisplayName("the snapshot is taken once and its DSL builds the view without NONE columns")
    void buildsTheView() {
        TableAccessResolution resolution = resolve("sample", "item");

        assertThat(ports.calls).containsExactly("snapshotFor:42", "product");
        assertThat(resolution).isInstanceOfSatisfying(TableAccessResolution.Resolved.class, resolved -> {
            TableAccessView view = resolved.view();
            assertThat(view.columns()).extracting(ColumnAccess::column).containsExactly("code", "name");
            assertThat(view.column("code").orElseThrow().valueType()).isEqualTo(ValueType.INTEGER);
            assertThat(view.operations()).containsExactlyInAnyOrder(AllowedOperation.LIST, AllowedOperation.DETAIL);
            assertThat(view.definitionId()).isEqualTo(new DefinitionId(BusinessDataDslFixture.HASH, 7L));
            assertThat(view.writeConfigured()).as("書き込みの口に設定がある（U2）").isTrue();
        });
    }

    @Test
    @DisplayName("without write settings the view says writing is not configured and allows no write operation")
    void writeNotConfigured() {
        ports.writeAccess.product = Optional.empty();

        TableAccessView view = ((TableAccessResolution.Resolved) resolve("sample", "item")).view();

        assertThat(view.writeConfigured()).isFalse();
        assertThat(view.operations()).doesNotContain(AllowedOperation.CREATE, AllowedOperation.UPDATE);
    }

    @Test
    @DisplayName("the view carries the write rules and the definition items of every visible column (BR14.1)")
    void writeRulesAndDefinitionItems() {
        TableAccessView view = ((TableAccessResolution.Resolved) resolve("sample", "item")).view();

        assertThat(view.writeRules("code")).isPresent();
        assertThat(view.writeRules("cost")).as("NONE のカラムは持たない").isEmpty();
        assertThat(view.columns()).allSatisfy(c -> assertThat(c.validation()).isNotNull());
        assertThat(view.foreignKeys()).isEmpty();
    }

    @Test
    @DisplayName("the names to quote are only those of the DSL, kept by kind")
    void knownIdentifiersByKind() {
        TableAccessView view = ((TableAccessResolution.Resolved) resolve("sample", "item")).view();

        assertThat(view.knownIdentifiers().schemas()).containsExactly("sample");
        assertThat(view.knownIdentifiers().tables()).containsExactlyInAnyOrder("item", "log", "secret");
        assertThat(view.knownIdentifiers().columns()).containsExactlyInAnyOrder("code", "name", "cost");
    }

    @Test
    @DisplayName("without an applied DSL the request is DSL_NOT_APPLIED")
    void noDsl() {
        ports.resolver.snapshot = PermissionSnapshot.of(BusinessDataServiceFixture.WORK_ROLE, null, null, Map.of());

        assertThat(resolve("sample", "item"))
                .isEqualTo(new TableAccessResolution.Rejected(ReadRejection.DSL_NOT_APPLIED));
    }

    @ParameterizedTest(name = "[{0}].[{1}]")
    @CsvSource(
            value = {
                "sample|nowhere",
                "other|item",
                "SAMPLE|item",
                "sample|ITEM",
                "sample|item\"; DROP TABLE item; --",
                "sample|item'--",
                "'sample;'|item"
            },
            delimiter = '|')
    @DisplayName("names that are not in the DSL, compared exactly, are TABLE_NOT_FOUND")
    void namesOutsideTheDsl(String schema, String table) {
        assertThat(resolve(schema, table)).isEqualTo(new TableAccessResolution.Rejected(ReadRejection.TABLE_NOT_FOUND));
        assertThat(resolve("sample", "item\nitem"))
                .isEqualTo(new TableAccessResolution.Rejected(ReadRejection.TABLE_NOT_FOUND));
        assertThat(resolve(null, null)).isEqualTo(new TableAccessResolution.Rejected(ReadRejection.TABLE_NOT_FOUND));
    }

    @Test
    @DisplayName("a table whose main permission is NONE is ACCESS_DENIED")
    void noneTable() {
        assertThat(resolve("sample", "secret"))
                .isEqualTo(new TableAccessResolution.Rejected(ReadRejection.ACCESS_DENIED));
    }

    @Test
    @DisplayName("an unknown database still builds the view with the narrowing type rules")
    void unknownDatabase() {
        ports.readAccess.product = Optional.empty();

        TableAccessView view = ((TableAccessResolution.Resolved) resolve("sample", "item")).view();

        assertThat(view.column("code").orElseThrow().valueType()).isEqualTo(ValueType.INTEGER);
        assertThat(view.column("name").orElseThrow().valueType()).isEqualTo(ValueType.STRING);
    }

    @Test
    @DisplayName("the resolution carries the snapshot it took once and target views reuse it without reading again")
    void referenceViewReusesTheSnapshot() {
        TableAccessResolution.Resolved resolved = (TableAccessResolution.Resolved) resolve("sample", "item");
        long snapshots =
                ports.calls.stream().filter(c -> c.startsWith("snapshotFor")).count();

        Optional<TableAccessView> log = resolver.referenceView(resolved.snapshot(), "sample", "log");

        assertThat(resolved.snapshot()).isSameAs(ports.resolver.snapshot);
        assertThat(resolved.toString()).doesNotContain("PermissionSnapshot");
        assertThat(log).hasValueSatisfying(v -> assertThat(v.table()).isEqualTo("log"));
        assertThat(ports.calls.stream().filter(c -> c.startsWith("snapshotFor")).count())
                .as("写しは取り直さない")
                .isEqualTo(snapshots)
                .isEqualTo(1L);
        assertThat(resolver.referenceView(resolved.snapshot(), "sample", "secret"))
                .as("NONE")
                .isEmpty();
        assertThat(resolver.referenceView(resolved.snapshot(), "sample", "nowhere"))
                .as("DSL に無い")
                .isEmpty();
        assertThat(resolver.referenceView(resolved.snapshot(), "other", "log"))
                .as("別のスキーマ")
                .isEmpty();
        assertThat(resolver.referenceView(resolved.snapshot(), "sample", null)).isEmpty();
        assertThat(resolver.referenceView(
                        PermissionSnapshot.of(BusinessDataServiceFixture.WORK_ROLE, null, null, Map.of()),
                        "sample",
                        "log"))
                .as("DSL が無い")
                .isEmpty();
    }
}
