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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.group.service.GroupMembershipQuery;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.repository.AssignedRoleRow;
import cherry.mastersmith.role.repository.PermissionSettingRepository;
import cherry.mastersmith.role.repository.PermissionSettingRow;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.role.repository.WorkRoleSelectionRepository;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 実効の権限の解決の口（契約 C5、FS の 2.9、BR5.3〜BR5.5・BR7.2、NFR2.3、計画の D-15・D-16）の単体テスト。
 */
class EffectivePermissionResolverImplTest {

    private static final long USER = 8L;

    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);

    private final WorkRoleSelectionRepository selections = mock(WorkRoleSelectionRepository.class);

    private final GroupMembershipQuery groups = mock(GroupMembershipQuery.class);

    private final PermissionSettingRepository settings = mock(PermissionSettingRepository.class);

    private final ActiveDslModelProvider activeDsl = mock(ActiveDslModelProvider.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    private final EffectivePermissionResolverImpl resolver = new EffectivePermissionResolverImpl(
            new UserRoleReader(assignments, groups, selections), settings, activeDsl, recording);

    EffectivePermissionResolverImplTest() {
        when(groups.groupIdsOfUser(USER)).thenReturn(Set.of());
        when(selections.findRoleIdOfUser(USER)).thenReturn(Optional.of(9L));
        when(assignments.findDirectRolesOfUser(USER))
                .thenReturn(List.of(new AssignedRoleRow(3L, "経理"), new AssignedRoleRow(9L, "総務")));
        when(activeDsl.current()).thenReturn(new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH));
    }

    private static PermissionSettingRow row(String schema, String table, String column, MainPermission main) {
        return new PermissionSettingRow(schema, table, column, main, null, null);
    }

    @Test
    @DisplayName(
            "the effective work role is the stored one while assigned, without reading the group roles of no group")
    void effectiveWorkRole() {
        assertThat(resolver.effectiveWorkRole(USER)).contains(new WorkRoleRef(9L, "総務"));

        verify(assignments, never()).findRolesOfGroups(any());
        assertThat(recording.definitions()).singleElement().matches(definition -> definition.isReadOnly());
    }

    @Test
    @DisplayName("the snapshot reads the settings of the work role only, once, and answers through the inheritance")
    void snapshot() {
        when(settings.findAllOfRole(9L))
                .thenReturn(List.of(
                        row("SALES", "", "", MainPermission.READ),
                        row("SALES", "ORDER_LINE", "", MainPermission.FULL),
                        row("SALES", "ORDER_LINE", "QTY", MainPermission.NONE)));

        PermissionSnapshot snapshot = resolver.snapshotFor(USER);

        verify(settings).findAllOfRole(9L);
        verify(settings, never()).findAllOfRole(3L);
        assertThat(snapshot.workRole()).contains(new WorkRoleRef(9L, "総務"));
        assertThat(snapshot.dslHash()).contains(RoleDslFixture.HASH);
        assertThat(snapshot.main("SALES", "ORDER_HEAD", null)).isEqualTo(MainPermission.READ);
        assertThat(snapshot.main("SALES", "ORDER_LINE", "QTY")).isEqualTo(MainPermission.NONE);
        assertThat(snapshot.main("SALES", "ORDER_LINE", "ORDER_NO")).isEqualTo(MainPermission.FULL);
    }

    @Test
    @DisplayName("without a role or without a DSL the snapshot is NONE and the settings are not read")
    void snapshotWithoutRoleOrDsl() {
        when(activeDsl.current()).thenReturn(new ActiveDsl.Absent());
        PermissionSnapshot noDsl = resolver.snapshotFor(USER);

        when(activeDsl.current()).thenReturn(new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH));
        when(assignments.findDirectRolesOfUser(USER)).thenReturn(List.of());
        PermissionSnapshot noRole = resolver.snapshotFor(USER);

        assertThat(noDsl.workRole()).contains(new WorkRoleRef(9L, "総務"));
        assertThat(noDsl.dslHash()).isEmpty();
        assertThat(noDsl.effective(PermissionTarget.schema("SALES"))).isEqualTo(EffectivePermission.NONE);
        assertThat(noRole.workRole()).isEmpty();
        assertThat(noRole.effective(PermissionTarget.schema("SALES"))).isEqualTo(EffectivePermission.NONE);
        verify(settings, never()).findAllOfRole(anyLong());
    }

    @Test
    @DisplayName("resolve reads only the ancestors of the target and applies the inheritance")
    void resolve() {
        when(settings.findAncestors(9L, "SALES", "ORDER_LINE", "QTY"))
                .thenReturn(List.of(
                        new PermissionSettingRow("SALES", "", "", MainPermission.READ, true, null),
                        row("SALES", "ORDER_LINE", "", MainPermission.FULL),
                        row("SALES", "ORDER_LINE", "QTY", MainPermission.NONE)));

        EffectivePermission permission = resolver.resolve(USER, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"));

        assertThat(permission).isEqualTo(new EffectivePermission(MainPermission.NONE, true, false));
        verify(settings, never()).findAllOfRole(anyLong());
    }

    @Test
    @DisplayName("resolve answers NONE without reading settings for no role, no DSL or a target outside the DSL")
    void resolveNone() {
        assertThat(resolver.resolve(USER, PermissionTarget.table("SALES", "NO_SUCH")))
                .isEqualTo(EffectivePermission.NONE);
        when(activeDsl.current()).thenReturn(new ActiveDsl.Absent());
        assertThat(resolver.resolve(USER, PermissionTarget.schema("SALES"))).isEqualTo(EffectivePermission.NONE);
        when(activeDsl.current()).thenReturn(new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH));
        when(assignments.findDirectRolesOfUser(USER)).thenReturn(List.of());
        assertThat(resolver.resolve(USER, PermissionTarget.schema("SALES"))).isEqualTo(EffectivePermission.NONE);

        verify(settings, never()).findAncestors(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a DB failure is thrown as it is, as an unexpected failure")
    void failureIsThrown() {
        when(assignments.findDirectRolesOfUser(USER)).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> resolver.snapshotFor(USER)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(() -> resolver.resolve(USER, null)).isInstanceOf(NullPointerException.class);
    }
}
