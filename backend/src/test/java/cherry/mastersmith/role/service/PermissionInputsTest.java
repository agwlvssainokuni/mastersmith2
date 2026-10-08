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
package cherry.mastersmith.role.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.service.PermissionInputs.EntryInput;
import cherry.mastersmith.role.service.PermissionInputs.TargetInput;
import cherry.mastersmith.role.service.PermissionInputs.Validation;
import cherry.mastersmith.user.domain.FieldErrorReason;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 権限の保存と消す操作の入力の判定（BR4.1・BR4.3・BR4.4・BR4.7・BR2.3）の単体テスト。 */
class PermissionInputsTest {

    private static final TargetInput SCHEMA_SCOPE = new TargetInput("SALES", null, null);

    private static final TargetInput TABLE_SCOPE = new TargetInput("SALES", "ORDER_LINE", null);

    private static EntryInput entry(String schema, String table, String column, String main) {
        return new EntryInput(schema, table, column, main, null, null);
    }

    private static Validation<?> invalid(String field, FieldErrorReason reason) {
        return new Validation.Invalid<>(field, reason);
    }

    @Test
    @DisplayName("a table scope accepts the table and its columns, keeps the order and reads null as not set")
    void validTableScope() {
        Validation<PermissionInputs.SaveCommand> result = PermissionInputs.save(
                TABLE_SCOPE,
                List.of(
                        new EntryInput("SALES", "ORDER_LINE", null, null, true, null),
                        entry("SALES", "ORDER_LINE", "QTY", "NONE")));

        assertThat(result).isInstanceOf(Validation.Valid.class);
        PermissionInputs.SaveCommand command = ((Validation.Valid<PermissionInputs.SaveCommand>) result).value();
        assertThat(command.scope()).isEqualTo(PermissionTarget.table("SALES", "ORDER_LINE"));
        assertThat(command.entries())
                .containsExactly(
                        Map.entry(
                                PermissionTarget.table("SALES", "ORDER_LINE"), new PermissionValues(null, true, null)),
                        Map.entry(
                                PermissionTarget.column("SALES", "ORDER_LINE", "QTY"),
                                new PermissionValues(MainPermission.NONE, null, null)));
        assertThat(command.toString()).doesNotContain("ORDER_LINE");
    }

    @Test
    @DisplayName("a schema scope accepts only the schema itself, other targets are outside the scope")
    void schemaScope() {
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, List.of(entry("SALES", null, null, "READ"))))
                .isInstanceOf(Validation.Valid.class);
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, List.of(entry("SALES", "ORDER_LINE", null, "READ"))))
                .isEqualTo(invalid("entries[0]", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(TABLE_SCOPE, List.of(entry("SALES", "ORDER_HEAD", null, "READ"))))
                .isEqualTo(invalid("entries[0]", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(TABLE_SCOPE, List.of(entry("HR", "ORDER_LINE", null, "READ"))))
                .isEqualTo(invalid("entries[0]", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    @DisplayName("missing scope or entries, values that are not allowed and column auxiliaries are refused")
    void refusedValues() {
        assertThat(PermissionInputs.save(null, List.of(entry("SALES", null, null, "READ"))))
                .isEqualTo(invalid("scope", FieldErrorReason.REQUIRED));
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, List.of()))
                .isEqualTo(invalid("entries", FieldErrorReason.REQUIRED));
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, null)).isEqualTo(invalid("entries", FieldErrorReason.REQUIRED));
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, List.of(entry("SALES", null, null, "ADMIN"))))
                .isEqualTo(invalid("entries[0].main", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, List.of(entry("SALES", null, null, "read"))))
                .isEqualTo(invalid("entries[0].main", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(
                        TABLE_SCOPE, List.of(new EntryInput("SALES", "ORDER_LINE", "QTY", null, null, false))))
                .isEqualTo(invalid("entries[0].delete", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(new TargetInput("SALES", "T", "C"), List.of(entry("SALES", null, null, null))))
                .isEqualTo(invalid("scope.columnName", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(
                        SCHEMA_SCOPE,
                        Arrays.asList(entry("SALES", null, null, "READ"), entry("SALES", null, null, "FULL"))))
                .isEqualTo(invalid("entries[1]", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    @DisplayName("names are checked for blank, length and control characters, and a column needs a table")
    void names() {
        assertThat(PermissionInputs.save(new TargetInput("", null, null), List.of(entry("S", null, null, null))))
                .isEqualTo(invalid("scope.schemaName", FieldErrorReason.REQUIRED));
        assertThat(PermissionInputs.save(new TargetInput("S", "", null), List.of(entry("S", null, null, null))))
                .isEqualTo(invalid("scope.tableName", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(SCHEMA_SCOPE, List.of(entry("SALES\n", null, null, null))))
                .isEqualTo(invalid("entries[0].schemaName", FieldErrorReason.INVALID_CHARACTER));
        assertThat(PermissionInputs.save(TABLE_SCOPE, List.of(entry("SALES", "ORDER_LINE", "C".repeat(129), null))))
                .isEqualTo(invalid("entries[0].columnName", FieldErrorReason.TOO_LONG));
        assertThat(PermissionInputs.save(TABLE_SCOPE, List.of(entry("SALES", null, "C", null))))
                .isEqualTo(invalid("entries[0].columnName", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.save(
                        new TargetInput(" SALES ", null, null), List.of(entry(" SALES ", null, null, null))))
                .as("前後の空白を取り除かない")
                .isInstanceOf(Validation.Valid.class);
    }

    @Test
    @DisplayName("clear targets need at least one target, valid names and no duplicates")
    void clearTargets() {
        assertThat(PermissionInputs.clear(null)).isEqualTo(invalid("targets", FieldErrorReason.REQUIRED));
        assertThat(PermissionInputs.clear(List.of())).isEqualTo(invalid("targets", FieldErrorReason.REQUIRED));
        assertThat(PermissionInputs.clear(
                        List.of(new TargetInput("OLD", "T", null), new TargetInput("OLD", "T", null))))
                .isEqualTo(invalid("targets[1]", FieldErrorReason.INVALID_VALUE));
        assertThat(PermissionInputs.clear(List.of(new TargetInput("OLD", null, "C"))))
                .isEqualTo(invalid("targets[0].columnName", FieldErrorReason.INVALID_VALUE));
        Validation<PermissionInputs.ClearCommand> valid =
                PermissionInputs.clear(List.of(new TargetInput("OLD", "T", "C"), new TargetInput("OLD", null, null)));
        assertThat(((Validation.Valid<PermissionInputs.ClearCommand>) valid)
                        .value()
                        .targets())
                .containsExactly(PermissionTarget.column("OLD", "T", "C"), PermissionTarget.schema("OLD"));
    }
}
