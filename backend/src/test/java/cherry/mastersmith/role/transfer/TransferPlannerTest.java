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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.domain.TransferAction;
import cherry.mastersmith.role.transfer.TransferPlanner.CurrentRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 変わる点の一覧（BR9.6〜BR9.9・BR9.12、{@code entities.md} の TransferPlan）の単体テスト。 */
class TransferPlannerTest {

    private static final PermissionValues READ = new PermissionValues(MainPermission.READ, null, null);

    private static final PermissionValues FULL = new PermissionValues(MainPermission.FULL, true, true);

    private static final PermissionTarget SALES = PermissionTarget.schema("SALES");

    private static final PermissionTarget ORDERS = PermissionTarget.table("SALES", "ORDERS");

    private static final PermissionTarget LEGACY = PermissionTarget.schema("LEGACY");

    private static final Predicate<PermissionTarget> IN_DSL =
            target -> !target.schemaName().equals("LEGACY");

    private static RoleName name(String value) {
        return ((RoleNameValidation.Valid) RoleName.parse(value)).name();
    }

    private static RoleTransferDocument.Role fileRole(String value, Map<PermissionTarget, PermissionValues> settings) {
        return new RoleTransferDocument.Role(name(value), settings);
    }

    private static CurrentRole current(long id, String value, Map<PermissionTarget, PermissionValues> settings) {
        return new CurrentRole(id, value, name(value).key(), settings);
    }

    @Test
    @DisplayName("a file role matching a current role by the name key replaces it and keeps the current name")
    void replaceAndCreate() {
        Map<PermissionTarget, PermissionValues> file = new LinkedHashMap<>();
        file.put(ORDERS, FULL);
        file.put(SALES, FULL);
        file.put(LEGACY, READ);
        RoleTransferDocument document =
                new RoleTransferDocument(List.of(fileRole("sales", file), fileRole("新規", Map.of(SALES, READ))));
        List<CurrentRole> current = List.of(
                current(30, "Admin", Map.of()),
                current(7, "Sales", Map.of(SALES, READ, PermissionTarget.schema("HR"), READ)),
                current(12, "Other", Map.of()));

        TransferPlan plan = TransferPlanner.plan(document, current, IN_DSL, "dsl-1");

        assertThat(plan.roles())
                .extracting(TransferRolePlan::name, TransferRolePlan::action, TransferRolePlan::roleId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Sales", TransferAction.REPLACE, 7L),
                        org.assertj.core.groups.Tuple.tuple("新規", TransferAction.CREATE, null));
        TransferRolePlan replaced = plan.roles().getFirst();
        assertThat(replaced.fileName().value()).isEqualTo("sales");
        assertThat(replaced.changes())
                .extracting(change -> change.target(), change -> change.reason(), change -> change.inCurrentDsl())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                PermissionTarget.schema("HR"), TransferChange.Reason.CLEARED, true),
                        org.assertj.core.groups.Tuple.tuple(LEGACY, TransferChange.Reason.ADDED, false),
                        org.assertj.core.groups.Tuple.tuple(SALES, TransferChange.Reason.CHANGED, true),
                        org.assertj.core.groups.Tuple.tuple(ORDERS, TransferChange.Reason.ADDED, true));
        assertThat(replaced.changes().get(2).before()).isEqualTo(READ);
        assertThat(replaced.changes().get(2).after()).isEqualTo(FULL);
        assertThat(plan.untouchedRoles()).containsExactly("Other", "Admin");
        assertThat(plan.replacedRoleIds()).containsExactly(7L);
        assertThat(plan.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("the counts per role go to the audit summary and roles are ordered by the name key")
    void countsAndOrder() {
        RoleTransferDocument document = new RoleTransferDocument(
                List.of(fileRole("b", Map.of(SALES, READ, LEGACY, READ)), fileRole("A", Map.of(ORDERS, READ))));

        TransferPlan plan = TransferPlanner.plan(
                document, List.of(current(5, "B", Map.of(ORDERS, READ, SALES, FULL))), IN_DSL, null);

        assertThat(plan.roles()).extracting(TransferRolePlan::name).containsExactly("A", "B");
        RoleAuditDetail.TransferRole audit = plan.roles().get(1).toAuditRole();
        assertThat(audit).isEqualTo(new RoleAuditDetail.TransferRole("B", 5L, TransferAction.REPLACE, 1, 1, 1, 1));
        assertThat(plan.toAuditDetail("f".repeat(64), plan.fingerprint()).roles())
                .hasSize(2);
        assertThat(plan.changeCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("a file that matches the current settings has no change and the plan is empty (BR9.12)")
    void emptyPlan() {
        RoleTransferDocument document = new RoleTransferDocument(List.of(fileRole("Sales", Map.of(SALES, READ))));

        TransferPlan same =
                TransferPlanner.plan(document, List.of(current(7, "Sales", Map.of(SALES, READ))), IN_DSL, null);
        TransferPlan created = TransferPlanner.plan(
                new RoleTransferDocument(List.of(fileRole("x", Map.of()))), List.of(), IN_DSL, null);
        TransferPlan nothing = TransferPlanner.plan(new RoleTransferDocument(List.of()), List.of(), IN_DSL, null);

        assertThat(same.isEmpty()).isTrue();
        assertThat(same.roles().getFirst().changes()).isEmpty();
        assertThat(created.isEmpty()).as("ロールを作るのは変わる点").isFalse();
        assertThat(nothing.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("the plan and its parts do not show names or values in their text")
    void textHidesNames() {
        TransferPlan plan = TransferPlanner.plan(
                new RoleTransferDocument(List.of(fileRole("LEAKCHECK_ROLE_7f3a", Map.of(SALES, READ)))),
                List.of(current(1, "SECRET_ROLE", Map.of())),
                IN_DSL,
                null);

        assertThat(plan.toString()
                        + plan.roles()
                        + plan.roles().getFirst().changes()
                        + new CurrentRole(1, "SECRET_ROLE", "secret_role", Map.of()))
                .doesNotContain("LEAKCHECK", "SALES", "READ", "SECRET");
    }

    @Test
    @DisplayName("inconsistent values are refused")
    void invariants() {
        assertThatThrownBy(() -> new TransferChange(SALES, READ, READ, TransferChange.Reason.CHANGED, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new TransferChange(SALES, PermissionValues.NOT_SET, READ, TransferChange.Reason.CLEARED, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new TransferChange(SALES, READ, PermissionValues.NOT_SET, TransferChange.Reason.ADDED, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferRolePlan("x", name("x"), 1L, TransferAction.CREATE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferPlan("ABC", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleTransferDocument.Role(name("x"), Map.of(SALES, PermissionValues.NOT_SET)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
