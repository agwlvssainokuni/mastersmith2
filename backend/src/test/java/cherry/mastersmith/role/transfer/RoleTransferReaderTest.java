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
package cherry.mastersmith.role.transfer;

import static cherry.mastersmith.role.testsupport.TransferYamlTrees.rejected;
import static cherry.mastersmith.role.testsupport.TransferYamlTrees.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.service.SafeYamlRejectionKind;
import cherry.mastersmith.dsl.service.SafeYamlResult;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.TransferError;
import cherry.mastersmith.role.domain.TransferError.Reason;
import cherry.mastersmith.role.testsupport.TransferYamlTrees;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 権限の YAML の木の検証（BR9.1・BR9.3・BR9.5、{@code security-design.md} 5節、試し U1）の単体テスト。 */
class RoleTransferReaderTest {

    private static RoleTransferDocument valid(String yaml) {
        RoleTransferReader.Result result = RoleTransferReader.read(tree(yaml));
        assertThat(result).isInstanceOf(RoleTransferReader.Valid.class);
        return ((RoleTransferReader.Valid) result).document();
    }

    private static List<TransferError> errors(String yaml) {
        RoleTransferReader.Result result = RoleTransferReader.read(tree(yaml));
        assertThat(result).isInstanceOf(RoleTransferReader.Invalid.class);
        return ((RoleTransferReader.Invalid) result).errors().errors();
    }

    private static List<Reason> reasons(String yaml) {
        return errors(yaml).stream().map(TransferError::reason).toList();
    }

    @Test
    @DisplayName("a document with all levels is read into the settings of each role in the file order")
    void allLevels() {
        RoleTransferDocument document = valid("""
                version: 1
                roles:
                  営業:
                    schemas:
                      SALES:
                        main: READ
                        create: true
                        delete: false
                        tables:
                          ORDERS:
                            main: FULL
                            columns:
                              PRICE: NONE
                  " Accounting ":
                    schemas: {}
                  empty: {}
                """);

        assertThat(document.roles())
                .extracting(role -> role.name().value())
                .containsExactly("営業", "Accounting", "empty");
        assertThat(document.roles().getFirst().settings())
                .containsExactly(
                        Map.entry(
                                PermissionTarget.schema("SALES"),
                                new PermissionValues(MainPermission.READ, true, false)),
                        Map.entry(
                                PermissionTarget.table("SALES", "ORDERS"),
                                new PermissionValues(MainPermission.FULL, null, null)),
                        Map.entry(
                                PermissionTarget.column("SALES", "ORDERS", "PRICE"),
                                new PermissionValues(MainPermission.NONE, null, null)));
        assertThat(document.roles().get(1).settings()).isEmpty();
        assertThat(document.toString()).doesNotContain("営業", "SALES", "READ");
    }

    @Test
    @DisplayName("an empty roles map is valid, and keys that look like other types are read as written (U1)")
    void emptyRolesAndTypedKeys() {
        assertThat(valid("version: 1\nroles: {}\n").roles()).isEmpty();

        RoleTransferDocument document = valid("""
                version: 1
                roles:
                  true:
                    schemas:
                      null: {main: READ}
                      123: {main: READ}
                      ~: {main: READ}
                      2026-10-06: {main: READ}
                      yes: {create: yes, delete: on}
                      no: {create: no}
                """);

        assertThat(document.roles().getFirst().name().value()).isEqualTo("true");
        assertThat(document.roles().getFirst().settings().keySet())
                .extracting(PermissionTarget::schemaName)
                .containsExactly("null", "123", "~", "2026-10-06", "yes", "no");
        assertThat(document.roles().getFirst().settings())
                .containsEntry(PermissionTarget.schema("yes"), new PermissionValues(null, true, true));
        assertThat(rejected("version: 1\nroles:\n  r:\n    schemas:\n      no: {create: no}\n      'no': {}\n")
                        .kind())
                .as("型によらず同じ文字列のキーは重複になる（U1）")
                .isEqualTo(SafeYamlRejectionKind.DUPLICATE_KEY);
    }

    @Test
    @DisplayName("a wrong version is the only error even when other parts are also wrong")
    void unsupportedVersion() {
        assertThat(errors("version: 2\nroles: [1]\nunknown: 1\n"))
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.reason()).isEqualTo(Reason.UNSUPPORTED_VERSION);
                    assertThat(error.path()).isEqualTo("version");
                    assertThat(error.line()).isEqualTo(1);
                });
        assertThat(reasons("version: '1'\nroles: {}\n")).containsExactly(Reason.UNSUPPORTED_VERSION);
        assertThat(reasons("version: 1.0\nroles: {}\n")).containsExactly(Reason.UNSUPPORTED_VERSION);
    }

    @Test
    @DisplayName("missing version and roles are reported at the root with the path of the missing key")
    void missingKeys() {
        List<TransferError> errors = errors("other: 1\n");

        assertThat(errors)
                .extracting(TransferError::reason, TransferError::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Reason.UNKNOWN_KEY, "other"),
                        org.assertj.core.groups.Tuple.tuple(Reason.MISSING_KEY, "version"),
                        org.assertj.core.groups.Tuple.tuple(Reason.MISSING_KEY, "roles"));
        assertThat(errors.get(1).line()).isEqualTo(1);
    }

    @Test
    @DisplayName("unknown keys are refused at every level")
    void unknownKeys() {
        assertThat(errors("""
                version: 1
                roles:
                  r:
                    owner: x
                    schemas:
                      S:
                        label: x
                        tables:
                          T:
                            schemas: x
                """))
                .extracting(TransferError::reason, TransferError::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Reason.UNKNOWN_KEY, "roles.r.owner"),
                        org.assertj.core.groups.Tuple.tuple(Reason.UNKNOWN_KEY, "roles.r.schemas.S.label"),
                        org.assertj.core.groups.Tuple.tuple(Reason.UNKNOWN_KEY, "roles.r.schemas.S.tables.T.schemas"));
    }

    @Test
    @DisplayName("only the decided words and booleans are values, and nested parts must be maps")
    void invalidValues() {
        assertThat(errors("""
                version: 1
                roles:
                  r:
                    schemas:
                      A: {main: read}
                      B: {main: 'true'}
                      C: {create: 'true'}
                      D: {delete: ~}
                      E: {create: 1}
                      F: {main: ~}
                      G: [READ]
                      H: {tables: [T]}
                      I: {tables: {T: {columns: [C]}}}
                      J: {tables: {T: {columns: {C: read}}}}
                      K: {tables: {T: {columns: {C: {main: READ}}}}}
                """))
                .extracting(TransferError::reason, TransferError::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.A.main"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.B.main"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.C.create"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.D.delete"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.E.create"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.F.main"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.G"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.H.tables"),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_VALUE, "roles.r.schemas.I.tables.T.columns"),
                        org.assertj.core.groups.Tuple.tuple(
                                Reason.INVALID_VALUE, "roles.r.schemas.J.tables.T.columns.C"),
                        org.assertj.core.groups.Tuple.tuple(
                                Reason.INVALID_VALUE, "roles.r.schemas.K.tables.T.columns.C"));
        assertThat(reasons("[1]\n")).containsExactly(Reason.INVALID_VALUE);
        assertThat(reasons("version: 1\nroles: [a]\n")).containsExactly(Reason.INVALID_VALUE);
        assertThat(reasons("version: 1\nroles:\n  r: READ\n")).containsExactly(Reason.INVALID_VALUE);
        assertThat(reasons("version: 1\nroles:\n  r: {schemas: [S]}\n")).containsExactly(Reason.INVALID_VALUE);
    }

    @Test
    @DisplayName("role names follow the name rule and a second role with the same key is a duplicate")
    void roleNames() {
        assertThat(errors("""
                version: 1
                roles:
                  "  ": {}
                  "%s": {}
                  Sales: {}
                  sales: {}
                  " Sales": {}
                """.formatted("r".repeat(65))))
                .extracting(TransferError::reason, TransferError::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_ROLE_NAME, "roles.  "),
                        org.assertj.core.groups.Tuple.tuple(Reason.INVALID_ROLE_NAME, "roles." + "r".repeat(65)),
                        org.assertj.core.groups.Tuple.tuple(Reason.DUPLICATE_ROLE, "roles.sales"),
                        org.assertj.core.groups.Tuple.tuple(Reason.DUPLICATE_ROLE, "roles. Sales"));
        assertThat(valid("version: 1\nroles:\n  \"%s\": {}\n".formatted("r".repeat(64)))
                        .roles())
                .hasSize(1);
    }

    @Test
    @DisplayName("target names follow the name rule and the parts under a wrong name are not checked")
    void targetNames() {
        String tooLong = "s".repeat(129);
        assertThat(errors("""
                version: 1
                roles:
                  r:
                    schemas:
                      "%s": {main: wrong}
                      S:
                        tables:
                          "%s": {}
                          T:
                            columns:
                              "%s": READ
                """.formatted(tooLong, tooLong, tooLong)))
                .extracting(TransferError::reason)
                .containsExactly(Reason.INVALID_TARGET_NAME, Reason.INVALID_TARGET_NAME, Reason.INVALID_TARGET_NAME);
        assertThat(valid("version: 1\nroles:\n  r:\n    schemas:\n      \" a/b ;%%\": {main: READ}\n"
                                .replace("%%", "%"))
                        .roles()
                        .getFirst()
                        .settings())
                .containsKey(PermissionTarget.schema(" a/b ;%"));
    }

    @Test
    @DisplayName("a column with CREATE or DELETE is refused because a column has only the main permission")
    void columnAuxiliary() {
        assertThat(errors("""
                version: 1
                roles:
                  r:
                    schemas:
                      S:
                        tables:
                          T:
                            columns:
                              C: {main: READ, create: true}
                              D: {delete: false}
                """))
                .extracting(TransferError::reason, TransferError::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                Reason.COLUMN_AUX_NOT_ALLOWED, "roles.r.schemas.S.tables.T.columns.C"),
                        org.assertj.core.groups.Tuple.tuple(
                                Reason.COLUMN_AUX_NOT_ALLOWED, "roles.r.schemas.S.tables.T.columns.D"));
    }

    @Test
    @DisplayName("at most 100 errors are collected and the search stops there")
    void atMostOneHundred() {
        String yaml = "version: 1\nroles:\n"
                + IntStream.range(0, 150)
                        .mapToObj(i -> "  r" + i + ": {schemas: {S: {main: wrong}}}\n")
                        .collect(Collectors.joining());

        List<TransferError> errors = errors(yaml);

        assertThat(errors).hasSize(TransferError.MAX_ERRORS);
        assertThat(errors.getLast().path()).isEqualTo("roles.r99.schemas.S.main");
    }

    @Test
    @DisplayName("an error has the line and column of the key and never the value that was written")
    void positionsWithoutValues() {
        TransferError error = errors("version: 1\nroles:\n  r:\n    schemas:\n      S:\n        main: SECRET_VALUE\n")
                .getFirst();

        assertThat(error.line()).isEqualTo(6);
        assertThat(error.column()).isEqualTo(9);
        assertThat(error.path()).isEqualTo("roles.r.schemas.S.main");
        assertThat(error.toString()).doesNotContain("SECRET_VALUE", "roles.r");
        assertThat(TransferError.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("reason", "line", "column", "path");
        assertThatThrownBy(() -> new TransferError(Reason.SYNTAX, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferError(Reason.SYNTAX, 0, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferError(Reason.SYNTAX, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = SafeYamlRejectionKind.class,
            names = {"TOO_LARGE"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("each refusal of the safe reader is mapped to one error with its position")
    void readerRefusals(SafeYamlRejectionKind kind) {
        TransferErrors errors = RoleTransferReader.rejected(new SafeYamlResult.Rejected(kind, 3, 5, "roles.r"));

        assertThat(errors.errors()).singleElement().satisfies(error -> {
            assertThat(error.reason().name()).isEqualTo(kind.name());
            assertThat(error.line()).isEqualTo(3);
            assertThat(error.path()).isEqualTo("roles.r");
        });
        assertThat(errors.toString()).isEqualTo("TransferErrors[count=1]");
    }

    @Test
    @DisplayName(
            "tags, deep nesting, collection aliases, syntax errors and too long lines are refused by the safe reader")
    void realRefusals() {
        assertThat(rejected("version: 1\nroles: !!map {}\n").kind()).isEqualTo(SafeYamlRejectionKind.TAG_NOT_ALLOWED);
        assertThat(rejected("version: 1\nroles: {a: &x {schemas: {}}, b: *x}\n").kind())
                .isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(rejected("a: {b: {c: {d: {e: {f: {g: {h: {i: {j: {k: 1}}}}}}}}}}\n")
                        .kind())
                .isEqualTo(SafeYamlRejectionKind.TOO_DEEP);
        assertThat(rejected("version: 1\nroles: {\n").kind()).isEqualTo(SafeYamlRejectionKind.SYNTAX);
        assertThat(rejected("version: 1\nroles: {}\n#" + "x".repeat(65_536) + "\n"))
                .as("1行の長さの上限（65,536）を超える行は大きさの区分で拒否する")
                .satisfies(refusal -> {
                    assertThat(refusal.kind()).isEqualTo(SafeYamlRejectionKind.TOO_LARGE);
                    assertThat(refusal.line()).isEqualTo(3);
                });
        assertThat(TransferYamlTrees.read("version: 1\nroles: {}\n#" + "x".repeat(65_535) + "\n"))
                .isInstanceOf(SafeYamlResult.Parsed.class);
        assertThatThrownBy(() -> RoleTransferReader.rejected(
                        new SafeYamlResult.Rejected(SafeYamlRejectionKind.TOO_LARGE, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferErrors(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 展開後の節の数がちょうど nodes になる流れの書き方の並び（根・2つのキー・版の値・並び = 5 節と要素。計画の D-38）。1行の長さの
     * 上限（65,536）の内に収めるため、要素 1,000 個ごとに改行して続ける。
     */
    private static String flowSequence(int nodes) {
        return "version: 1\nroles: ["
                + IntStream.range(0, nodes - 5)
                        .mapToObj(i -> i > 0 && i % 1_000 == 0 ? "\n  1" : "1")
                        .collect(Collectors.joining(","))
                + "]\n";
    }

    @Test
    @DisplayName("500,000 expanded nodes reach the content check and 500,001 are TOO_MANY_ALIASES (NFR1.5, D-38)")
    void expandedNodeBoundary() {
        assertThat(reasons(flowSequence(500_000))).containsExactly(Reason.INVALID_VALUE);

        SafeYamlResult.Rejected over = rejected(flowSequence(500_001));
        assertThat(over.kind()).isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(RoleTransferReader.rejected(over).errors())
                .extracting(TransferError::reason)
                .containsExactly(Reason.TOO_MANY_ALIASES);
    }
}
