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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.repository.PermissionLevelRow;
import cherry.mastersmith.role.repository.PermissionSettingRepository;
import cherry.mastersmith.role.repository.PermissionSettingRow;
import cherry.mastersmith.role.repository.RoleRepository;
import cherry.mastersmith.role.repository.RoleView;
import cherry.mastersmith.role.store.Referent;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * ロールの管理と権限の設定の業務処理（FS の 2.1〜2.7、BR3.6・BR4.4〜BR4.9・BR8.1・BR8.3〜BR8.5・BR11.1〜BR11.3、NFR3.4、計画の
 * 4.3・D-4）の単体テスト。store・repository・DSL・待ち合わせの口・出来事の知らせはモックにし、トランザクションは記録だけの管理に任せる。
 */
class RoleAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");

    private static final long ACTOR = 1L;

    private static final long ROLE = 50L;

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.50", "Mozilla/5.0", "trace-0050");

    private static final PermissionTarget LINE = PermissionTarget.table("SALES", "ORDER_LINE");

    private static final PermissionTarget QTY = PermissionTarget.column("SALES", "ORDER_LINE", "QTY");

    private final RoleStore store = mock(RoleStore.class);

    private final RoleRepository roles = mock(RoleRepository.class);

    private final PermissionSettingRepository settings = mock(PermissionSettingRepository.class);

    private final ActiveDslModelProvider activeDsl = mock(ActiveDslModelProvider.class);

    private final RoleBarrier barrier = mock(RoleBarrier.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    /** 出来事を知らせた時点の、巻き戻しの回数と確定の回数。 */
    private final List<String> publishedAt = new ArrayList<>();

    private final List<RoleAuditEvent> events = new ArrayList<>();

    private final RoleAdminService service = new RoleAdminService(
            new RoleStoreTransactions(store, recording),
            roles,
            settings,
            activeDsl,
            barrier,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            recording);

    RoleAdminServiceTest() {
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    publishedAt.add("rollbacks=" + recording.rollbacks() + ",commits=" + recording.commits());
                    return null;
                })
                .when(publisher)
                .publishEvent(any(Object.class));
        when(activeDsl.current()).thenReturn(new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH));
    }

    private static Role role(long id, String name) {
        Role role = mock(Role.class);
        when(role.getRoleId()).thenReturn(id);
        when(role.getName()).thenReturn(name);
        when(role.getCreatedAt()).thenReturn(NOW);
        when(role.getUpdatedAt()).thenReturn(NOW);
        return role;
    }

    private Role locked(String name) {
        Role locked = role(ROLE, name);
        when(store.lockRole(ROLE)).thenReturn(new RoleStoreOutcome.Done<>(locked));
        return locked;
    }

    private RoleAuditEvent onlyEvent() {
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    /** 1つ目を巻き戻した後の、2つ目のトランザクションで失敗の出来事を出したことを確かめる。 */
    private void failurePublishedAfterRollback() {
        assertThat(publishedAt).containsExactly("rollbacks=1,commits=0");
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        assertThat(recording.rollbacks()).isEqualTo(1);
        assertThat(recording.commits()).as("2つ目のトランザクション").isEqualTo(1);
    }

    private static PermissionInputs.SaveCommand save(
            PermissionTarget scope, Map<PermissionTarget, PermissionValues> entries) {
        return new PermissionInputs.SaveCommand(scope, entries);
    }

    @Test
    @DisplayName("an invalid name is refused before any transaction, store call or event")
    void invalidNames() {
        assertThat(service.create(ACTOR, ORIGIN, " 　 "))
                .isEqualTo(new RoleCreateResult.InvalidName(RoleNameValidation.Reason.INVALID_BLANK));
        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "a".repeat(65)))
                .isEqualTo(new RoleChangeResult.InvalidName(RoleNameValidation.Reason.INVALID_TOO_LONG));

        assertThat(recording.definitions()).isEmpty();
        verifyNoInteractions(store, publisher, barrier);
    }

    @Test
    @DisplayName("a creation checks the key, waits at the check and write points, writes and commits one success event")
    void createSucceeds() {
        Role created = role(51L, "営業");
        when(roles.findIdByNameKey("営業")).thenReturn(Optional.empty());
        when(store.insertRole(any(), eq(NOW))).thenReturn(new RoleStoreOutcome.Done<>(created));

        RoleCreateResult result = service.create(ACTOR, ORIGIN, " 営業 ");

        assertThat(result).isEqualTo(new RoleCreateResult.Created(new RoleDetail(51L, "営業", NOW, NOW)));
        InOrder order = inOrder(roles, barrier, store, publisher);
        order.verify(roles).findIdByNameKey("営業");
        order.verify(barrier).afterCheck(RoleOperation.CREATE, "営業");
        order.verify(store).insertRole(any(), eq(NOW));
        order.verify(barrier).afterWrite(RoleOperation.CREATE, "営業");
        order.verify(publisher).publishEvent(any(Object.class));
        assertThat(onlyEvent())
                .isEqualTo(RoleAuditEvent.succeeded(
                        RoleOperation.CREATE, ACTOR, 51L, new RoleAuditDetail.Name("営業"), NOW, ORIGIN));
        assertThat(publishedAt).containsExactly("rollbacks=0,commits=0");
        assertThat(recording.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("a duplicate found before the write and a key violation are both NAME_DUPLICATE after a rollback")
    void createDuplicate() {
        when(roles.findIdByNameKey("sales")).thenReturn(Optional.of(9L));

        assertThat(service.create(ACTOR, ORIGIN, "Sales"))
                .isEqualTo(new RoleCreateResult.Rejected(RoleRejection.NAME_DUPLICATE));
        verify(store, never()).insertRole(any(), any());
        failurePublishedAfterRollback();
        assertThat(onlyEvent())
                .isEqualTo(RoleAuditEvent.failed(
                        RoleOperation.CREATE,
                        ACTOR,
                        null,
                        RoleAuditFailure.ROLE_NAME_DUPLICATE,
                        new RoleAuditDetail.Name("Sales"),
                        NOW,
                        ORIGIN));

        events.clear();
        publishedAt.clear();
        when(roles.findIdByNameKey("hr")).thenReturn(Optional.empty());
        when(store.insertRole(any(), any())).thenReturn(new RoleStoreOutcome.NameTaken<>());
        assertThat(service.create(ACTOR, ORIGIN, "HR"))
                .isEqualTo(new RoleCreateResult.Rejected(RoleRejection.NAME_DUPLICATE));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.ROLE_NAME_DUPLICATE);
        verify(barrier, never()).afterWrite(eq(RoleOperation.CREATE), any());
    }

    @Test
    @DisplayName("a key wait timeout is ROLE_BUSY without any event")
    void createBusy() {
        when(roles.findIdByNameKey(any())).thenReturn(Optional.empty());
        when(store.insertRole(any(), any())).thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_NAME_KEY));

        assertThat(service.create(ACTOR, ORIGIN, "営業")).isEqualTo(new RoleCreateResult.Busy());
        assertThat(events).isEmpty();
        assertThat(recording.rollbacks()).isEqualTo(1);
        assertThat(recording.commits()).isZero();
    }

    @Test
    @DisplayName("a rename is refused in the order missing role, no change, duplicate (BR3.6)")
    void renameOrder() {
        when(store.lockRole(ROLE)).thenReturn(new RoleStoreOutcome.RoleMissing<>());
        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "営業"))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.ROLE_NOT_FOUND));
        assertThat(onlyEvent())
                .isEqualTo(RoleAuditEvent.failed(
                        RoleOperation.RENAME, ACTOR, ROLE, RoleAuditFailure.ROLE_NOT_FOUND, null, NOW, ORIGIN));
        failurePublishedAfterRollback();

        events.clear();
        locked("営業");
        when(roles.findIdByNameKey("営業")).thenReturn(Optional.of(ROLE));
        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "営業"))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.NO_CHANGE));
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Name("営業"));

        events.clear();
        when(roles.findIdByNameKey("総務")).thenReturn(Optional.of(77L));
        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "総務"))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.NAME_DUPLICATE));
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Rename("営業", "総務"));
        verify(store, never()).renameRole(any(), any(), any());
    }

    @Test
    @DisplayName("a case-only rename keeps the own key, waits at the four points and records the rename")
    void renameCaseOnly() {
        Role locked = locked("sales");
        when(roles.findIdByNameKey("sales")).thenReturn(Optional.of(ROLE));
        when(store.renameRole(eq(locked), any(), eq(NOW))).thenReturn(new RoleStoreOutcome.Done<>(locked));

        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "Sales")).isEqualTo(new RoleChangeResult.Done());

        InOrder order = inOrder(barrier, store);
        order.verify(barrier).beforeLock(RoleOperation.RENAME, ROLE);
        order.verify(store).lockRole(ROLE);
        order.verify(barrier).afterLock(RoleOperation.RENAME, ROLE);
        order.verify(barrier).afterCheck(RoleOperation.RENAME, "sales");
        order.verify(store).renameRole(eq(locked), any(), eq(NOW));
        order.verify(barrier).afterWrite(RoleOperation.RENAME, "sales");
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Rename("sales", "Sales"));
    }

    @Test
    @DisplayName(
            "a row lock timeout and a key timeout on rename are ROLE_BUSY without events, a key violation is a duplicate")
    void renameBusyAndViolation() {
        when(store.lockRole(ROLE)).thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_ROW));
        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "営業")).isEqualTo(new RoleChangeResult.Busy());
        assertThat(events).isEmpty();

        Role locked = locked("営業");
        when(roles.findIdByNameKey(any())).thenReturn(Optional.empty());
        when(store.renameRole(eq(locked), any(), any())).thenReturn(new RoleStoreOutcome.NameTaken<>());
        assertThat(service.rename(ACTOR, ORIGIN, ROLE, "総務"))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.NAME_DUPLICATE));
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Rename("営業", "総務"));
    }

    @Test
    @DisplayName("a delete removes the role and records its name, a role foreign key is read as ROLE_IN_USE")
    void delete() {
        Role locked = locked("営業");
        when(store.deleteRole(locked)).thenReturn(new RoleStoreOutcome.Done<>(null));

        assertThat(service.delete(ACTOR, ORIGIN, ROLE)).isEqualTo(new RoleChangeResult.Done());
        verify(barrier).afterWrite(RoleOperation.DELETE, String.valueOf(ROLE));
        assertThat(onlyEvent())
                .isEqualTo(RoleAuditEvent.succeeded(
                        RoleOperation.DELETE, ACTOR, ROLE, new RoleAuditDetail.Name("営業"), NOW, ORIGIN));

        events.clear();
        when(store.deleteRole(locked)).thenReturn(new RoleStoreOutcome.Referenced<>(Referent.ROLE));
        assertThat(service.delete(ACTOR, ORIGIN, ROLE)).isEqualTo(new RoleChangeResult.InUse(0, 0));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.ROLE_IN_USE);
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.InUse("営業", 0, 0));
    }

    @Test
    @DisplayName("a save without an applied DSL or with a value outside the DSL is refused without writing")
    void saveRefusedByTheDsl() {
        locked("営業");
        when(activeDsl.current()).thenReturn(new ActiveDsl.Absent());
        assertThat(service.savePermissions(
                        ACTOR,
                        ORIGIN,
                        ROLE,
                        save(LINE, Map.of(LINE, new PermissionValues(MainPermission.READ, null, null)))))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.DSL_NOT_APPLIED));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.DSL_NOT_APPLIED);

        events.clear();
        when(activeDsl.current()).thenReturn(new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH));
        PermissionTarget gone = PermissionTarget.table("SALES", "GONE");
        assertThat(service.savePermissions(
                        ACTOR, ORIGIN, ROLE, save(gone, Map.of(gone, new PermissionValues(null, true, null)))))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.TARGET_NOT_IN_DSL));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.PERMISSION_TARGET_NOT_IN_DSL);
        verify(store, never()).writePermissions(any(), anyMap(), any());
    }

    @Test
    @DisplayName("a save writes only the changed targets, records their before and after values, and rejects no change")
    void saveDifference() {
        Role locked = locked("営業");
        when(settings.findInScope(ROLE, "SALES", "ORDER_LINE"))
                .thenReturn(
                        List.of(new PermissionSettingRow("SALES", "ORDER_LINE", "", MainPermission.READ, null, null)));
        when(store.writePermissions(eq(locked), anyMap(), eq(NOW))).thenReturn(new RoleStoreOutcome.Done<>(null));
        Map<PermissionTarget, PermissionValues> entries = new LinkedHashMap<>();
        entries.put(LINE, new PermissionValues(MainPermission.READ, null, null));
        entries.put(QTY, new PermissionValues(MainPermission.NONE, null, null));

        assertThat(service.savePermissions(ACTOR, ORIGIN, ROLE, save(LINE, entries)))
                .isEqualTo(new RoleChangeResult.Done());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<PermissionTarget, PermissionValues>> written = ArgumentCaptor.forClass(Map.class);
        verify(store).writePermissions(eq(locked), written.capture(), eq(NOW));
        assertThat(written.getValue()).containsOnlyKeys(QTY);
        verify(barrier).afterWrite(RoleOperation.CHANGE_PERMISSIONS, String.valueOf(ROLE));
        assertThat(onlyEvent().detail())
                .isEqualTo(new RoleAuditDetail.PermissionChanges(
                        "営業",
                        List.of(new RoleAuditDetail.PermissionChange(
                                QTY,
                                PermissionValues.NOT_SET,
                                new PermissionValues(MainPermission.NONE, null, null)))));

        events.clear();
        assertThat(service.savePermissions(
                        ACTOR,
                        ORIGIN,
                        ROLE,
                        save(LINE, Map.of(LINE, new PermissionValues(MainPermission.READ, null, null)))))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.NO_CHANGE));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.NO_CHANGE);
    }

    @Test
    @DisplayName("resetting a target outside the DSL to not set is accepted, and a write timeout is ROLE_BUSY")
    void saveResetAndBusy() {
        Role locked = locked("営業");
        PermissionTarget gone = PermissionTarget.table("SALES", "GONE");
        when(settings.findInScope(ROLE, "SALES", "GONE"))
                .thenReturn(List.of(new PermissionSettingRow("SALES", "GONE", "", MainPermission.FULL, null, null)));
        when(store.writePermissions(eq(locked), anyMap(), any()))
                .thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_ROW));

        assertThat(service.savePermissions(ACTOR, ORIGIN, ROLE, save(gone, Map.of(gone, PermissionValues.NOT_SET))))
                .isEqualTo(new RoleChangeResult.Busy());
        verify(store).writePermissions(eq(locked), eq(Map.of(gone, PermissionValues.NOT_SET)), any());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName(
            "clearing refuses a target in the current DSL, rejects when nothing is stored and removes stored targets")
    void clear() {
        assertThat(service.clearPermissions(ACTOR, ORIGIN, ROLE, new PermissionInputs.ClearCommand(List.of(LINE))))
                .isEqualTo(new RoleChangeResult.InvalidInput("targets[0]", FieldErrorReason.INVALID_VALUE));
        assertThat(recording.definitions()).isEmpty();

        Role locked = locked("営業");
        PermissionTarget gone = PermissionTarget.column("OLD", "T", "C");
        when(settings.findRow(ROLE, "OLD", "T", "C")).thenReturn(Optional.empty());
        assertThat(service.clearPermissions(ACTOR, ORIGIN, ROLE, new PermissionInputs.ClearCommand(List.of(gone))))
                .isEqualTo(new RoleChangeResult.Rejected(RoleRejection.NO_CHANGE));

        events.clear();
        when(settings.findRow(ROLE, "OLD", "T", "C"))
                .thenReturn(Optional.of(new PermissionSettingRow("OLD", "T", "C", MainPermission.READ, null, null)));
        when(store.clearPermissions(eq(locked), eq(List.of(gone)), eq(NOW))).thenReturn(new RoleStoreOutcome.Done<>(1));
        assertThat(service.clearPermissions(ACTOR, ORIGIN, ROLE, new PermissionInputs.ClearCommand(List.of(gone))))
                .isEqualTo(new RoleChangeResult.Done());
        assertThat(onlyEvent().detail())
                .isEqualTo(new RoleAuditDetail.PermissionChanges(
                        "営業",
                        List.of(new RoleAuditDetail.PermissionChange(
                                gone,
                                new PermissionValues(MainPermission.READ, null, null),
                                PermissionValues.NOT_SET))));
    }

    @Test
    @DisplayName("the list reads a page by id, refuses an invalid page and returns an empty page after the end")
    void list() {
        when(roles.countAll()).thenReturn(1L);
        when(roles.findPage(any())).thenReturn(List.of(new cherry.mastersmith.role.repository.RoleRowView(5L, "営業")));

        assertThat(service.list("0")).isEqualTo(new RoleListResult.InvalidPage());
        RoleListResult.Page page = ((RoleListResult.Listed) service.list(null)).page();
        assertThat(page.items()).containsExactly(new RoleListResult.Row(5L, "営業", 0, 0));
        assertThat(page.size()).isEqualTo(20);
        assertThat(((RoleListResult.Listed) service.list("2")).page().items()).isEmpty();
        assertThat(recording.definitions()).allMatch(definition -> definition.isReadOnly());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("the detail and the tree are read without events, and refuse a missing role or a missing DSL")
    void readings() {
        when(roles.findView(anyLong())).thenReturn(Optional.empty());
        when(roles.findView(ROLE)).thenReturn(Optional.of(new RoleView(ROLE, "営業", NOW, NOW)));
        assertThat(service.detail(ROLE)).isEqualTo(new RoleDetailResult.Found(new RoleDetail(ROLE, "営業", NOW, NOW)));
        assertThat(service.detail(-1L)).isEqualTo(new RoleDetailResult.NotFound());

        when(roles.existsRole(ROLE)).thenReturn(true);
        assertThat(service.schemas(99L)).isEqualTo(new PermissionTreeResult.RoleNotFound());
        when(settings.findTableLevel(ROLE, "SALES"))
                .thenReturn(List.of(
                        new PermissionLevelRow("", MainPermission.READ, null, null, 1),
                        new PermissionLevelRow("ORDER_HEAD", MainPermission.NONE, null, null, 1)));
        PermissionTreeResult tables = service.tables(ROLE, "SALES");
        assertThat(tables).isInstanceOf(PermissionTreeResult.Found.class);
        assertThat(((PermissionTreeResult.Found) tables).nodes())
                .extracting(node -> node.effective().main())
                .containsExactly(MainPermission.READ, MainPermission.NONE);
        assertThat(tables.toString()).isEqualTo("Found[nodes=2]");

        when(activeDsl.current()).thenReturn(new ActiveDsl.Absent());
        assertThat(service.columns(ROLE, "SALES", "ORDER_LINE")).isEqualTo(new PermissionTreeResult.DslNotApplied());
        verifyNoInteractions(publisher, store);
    }
}
