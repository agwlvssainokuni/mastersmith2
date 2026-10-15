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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dsl.service.DefaultSafeYamlReader;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.domain.TransferError;
import cherry.mastersmith.role.repository.PermissionSettingRepository;
import cherry.mastersmith.role.repository.RoleKeyView;
import cherry.mastersmith.role.repository.RoleRepository;
import cherry.mastersmith.role.repository.RoleSettingRow;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.role.store.TransferWrite;
import cherry.mastersmith.role.store.TransferWritten;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 権限の YAML の書き出し・確かめ・適用の業務処理（FS の 2.13〜2.15、BR9.2〜BR9.15・BR11.7、NFR3.8、{@code reliability-design.md} 2.5・2.6、
 * 計画の 4.3）の単体テスト。store・repository・DSL・待ち合わせの口・出来事の知らせはモックにし、上限つきの読み込みは本物、
 * トランザクションは記録だけの管理に任せる。
 */
class RoleTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T06:00:00Z");

    private static final long ACTOR = 1L;

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.60", "Mozilla/5.0", "trace-0060");

    private static final String YAML = """
            version: 1
            roles:
              Sales:
                schemas:
                  SALES: {main: FULL}
              新規:
                schemas:
                  HR: {main: READ}
            """;

    private final RoleStore store = mock(RoleStore.class);

    private final RoleRepository roles = mock(RoleRepository.class);

    private final PermissionSettingRepository settings = mock(PermissionSettingRepository.class);

    private final ActiveDslModelProvider activeDsl = mock(ActiveDslModelProvider.class);

    private final RoleTransferSlot slot = mock(RoleTransferSlot.class);

    private final RoleBarrier barrier = mock(RoleBarrier.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    private final List<RoleAuditEvent> events = new ArrayList<>();

    private final List<String> publishedAt = new ArrayList<>();

    private final Role sales = mock(Role.class);

    private final RoleTransferService service = service(new RoleTransferLimits(1_000));

    RoleTransferServiceTest() {
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    publishedAt.add("rollbacks=" + recording.rollbacks() + ",commits=" + recording.commits());
                    return null;
                })
                .when(publisher)
                .publishEvent(any(Object.class));
        when(activeDsl.current()).thenReturn(new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH));
        when(slot.tryAcquire()).thenReturn(true);
        when(sales.getRoleId()).thenReturn(7L);
        when(roles.findAllKeys())
                .thenReturn(List.of(new RoleKeyView(7, "Sales", "sales"), new RoleKeyView(9, "Other", "other")));
        when(settings.findAllOfRoles(any()))
                .thenReturn(List.of(new RoleSettingRow(7, "SALES", "", "", MainPermission.READ, null, null)));
        when(store.lockRole(7L)).thenReturn(new RoleStoreOutcome.Done<>(sales));
        when(store.applyTransfer(any(), any()))
                .thenReturn(new RoleStoreOutcome.Done<>(new TransferWritten(1, 1, 2, 1)));
    }

    private RoleTransferService service(RoleTransferLimits limits) {
        return new RoleTransferService(
                new RoleStoreTransactions(store, recording),
                roles,
                settings,
                activeDsl,
                new DefaultSafeYamlReader(new SafeYamlParser()),
                slot,
                limits,
                barrier,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                recording);
    }

    private static RoleTransferBody body(String yaml) {
        return new RoleTransferBody(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private String fingerprint() {
        return ((TransferCheckResult.Checked) service.check(body(YAML))).plan().fingerprint();
    }

    @Test
    @DisplayName(
            "a check builds the plan and the fingerprint without writing, locking or auditing, and releases the slot")
    void check() {
        TransferCheckResult result = service.check(body(YAML));

        assertThat(result).isInstanceOf(TransferCheckResult.Checked.class);
        var plan = ((TransferCheckResult.Checked) result).plan();
        assertThat(plan.roles()).extracting(role -> role.name()).containsExactly("Sales", "新規");
        assertThat(plan.untouchedRoles()).containsExactly("Other");
        assertThat(plan.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(result.toString()).doesNotContain("Sales", "SALES");
        verifyNoInteractions(store, barrier, publisher);
        verify(slot).release();
        assertThat(recording.definitions())
                .allSatisfy(definition -> assertThat(definition.isReadOnly()).isTrue());
    }

    @Test
    @DisplayName("a busy slot refuses before the DSL and the body are read, without auditing (2.6)")
    void busySlot() {
        when(slot.tryAcquire()).thenReturn(false);
        InputStream stream = mock(InputStream.class);

        assertThat(service.check(new RoleTransferBody(stream))).isInstanceOf(TransferCheckResult.Busy.class);
        assertThat(service.apply(ACTOR, ORIGIN, new RoleTransferBody(stream), "a".repeat(64)))
                .isInstanceOf(TransferApplyResult.Busy.class);
        verifyNoInteractions(stream, activeDsl, store, publisher);
        verify(slot, never()).release();
    }

    @Test
    @DisplayName("without a DSL the check is refused without auditing and the apply is refused with a failure audit")
    void dslAbsent() throws IOException {
        when(activeDsl.current()).thenReturn(new ActiveDsl.Absent());
        InputStream stream = mock(InputStream.class);
        String fingerprint = "b".repeat(64);

        assertThat(service.check(new RoleTransferBody(stream))).isInstanceOf(TransferCheckResult.DslNotApplied.class);
        assertThat(events).isEmpty();
        assertThat(service.apply(ACTOR, ORIGIN, new RoleTransferBody(stream), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.DSL_NOT_APPLIED));
        verifyNoInteractions(stream, store);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.operation()).isEqualTo(RoleOperation.APPLY_TRANSFER);
            assertThat(event.failure()).isEqualTo(RoleAuditFailure.DSL_NOT_APPLIED);
            assertThat(event.detail()).isEqualTo(new RoleAuditDetail.Transfer(null, fingerprint, List.of()));
        });
        verify(slot, org.mockito.Mockito.times(2)).release();
    }

    @Test
    @DisplayName("a body over the limit and a refused or invalid YAML are rejected without auditing")
    void tooLargeAndInvalid() {
        assertThat(service.check(body("version: 1\nroles: {}\n#" + "x".repeat(1_000))))
                .isInstanceOf(TransferCheckResult.TooLarge.class);
        assertThat(service.apply(ACTOR, ORIGIN, body("#" + "x".repeat(1_000)), "a".repeat(64)))
                .isInstanceOf(TransferApplyResult.TooLarge.class);
        TransferCheckResult tagged = service.check(body("version: 1\nroles: !!map {}\n"));
        TransferApplyResult invalid = service.apply(ACTOR, ORIGIN, body("version: 2\nroles: {}\n"), "a".repeat(64));

        assertThat(((TransferCheckResult.Invalid) tagged).errors().errors())
                .extracting(TransferError::reason)
                .containsExactly(TransferError.Reason.TAG_NOT_ALLOWED);
        assertThat(((TransferApplyResult.Invalid) invalid).errors().errors())
                .extracting(TransferError::reason)
                .containsExactly(TransferError.Reason.UNSUPPORTED_VERSION);
        assertThat(service(new RoleTransferLimits(1_000)).check(body("x".repeat(1_000))))
                .isNotInstanceOf(TransferCheckResult.TooLarge.class);
        String longLine = "version: 1\nroles: {}\n#" + "x".repeat(RoleTransferLimits.MAX_LINE_LENGTH) + "\n";
        RoleTransferService roomy = service(new RoleTransferLimits(200_000));
        assertThat(roomy.check(body(longLine)))
                .as("大きさの上限の内でも、1行の長さの上限を超えれば大きさの拒否（計画の D-3）")
                .isInstanceOf(TransferCheckResult.TooLarge.class);
        assertThat(roomy.apply(ACTOR, ORIGIN, body(longLine), "a".repeat(64)))
                .isInstanceOf(TransferApplyResult.TooLarge.class);
        assertThat(events).isEmpty();
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName(
            "an apply locks the replaced roles, rebuilds the plan, writes and audits one success inside the transaction")
    void applySuccess() {
        String fingerprint = fingerprint();

        TransferApplyResult result = service.apply(ACTOR, ORIGIN, body(YAML), fingerprint);

        assertThat(result).isEqualTo(new TransferApplyResult.Applied(1, 1));
        InOrder order = inOrder(barrier, store, publisher);
        order.verify(barrier).beforeLock(RoleOperation.APPLY_TRANSFER, 7L);
        order.verify(store).lockRole(7L);
        order.verify(barrier).afterLock(RoleOperation.APPLY_TRANSFER, 7L);
        order.verify(store).applyTransfer(any(), any());
        order.verify(barrier).afterWrite(RoleOperation.APPLY_TRANSFER, fingerprint.substring(0, 8));
        order.verify(publisher).publishEvent(any(Object.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransferWrite>> writes = ArgumentCaptor.forClass(List.class);
        verify(store).applyTransfer(writes.capture(), any());
        assertThat(writes.getValue())
                .extracting(write -> write.replaced() != null)
                .containsExactly(true, false);
        assertThat(writes.getValue().get(1).created().value()).isEqualTo("新規");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.succeeded()).isTrue();
            assertThat(event.targetRoleId()).isNull();
            RoleAuditDetail.Transfer detail = (RoleAuditDetail.Transfer) event.detail();
            assertThat(detail.fingerprint()).isEqualTo(fingerprint);
            assertThat(detail.fileSha256()).matches("[0-9a-f]{64}");
            assertThat(detail.roles()).hasSize(2);
        });
        assertThat(publishedAt).containsExactly("rollbacks=0,commits=1");
    }

    @Test
    @DisplayName("a different fingerprint is stale: rolled back and a failure audit in a second transaction")
    void stale() {
        TransferApplyResult result = service.apply(ACTOR, ORIGIN, body(YAML), "c".repeat(64));

        assertThat(result).isEqualTo(new TransferApplyResult.Rejected(RoleRejection.TRANSFER_STALE));
        verify(store, never()).applyTransfer(any(), any());
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.failure()).isEqualTo(RoleAuditFailure.ROLE_TRANSFER_STALE);
            assertThat(((RoleAuditDetail.Transfer) event.detail()).fingerprint())
                    .isEqualTo("c".repeat(64));
        });
        assertThat(publishedAt).as("1つ目を巻き戻した後、2つ目の確定の前に出す").containsExactly("rollbacks=1,commits=0");
    }

    @Test
    @DisplayName(
            "a file equal to the current settings is NO_CHANGE and a vanished role or DSL is refused in the transaction")
    void noChangeAndVanished() {
        String same = "version: 1\nroles:\n  Sales:\n    schemas:\n      SALES: {main: READ}\n";
        String fingerprint =
                ((TransferCheckResult.Checked) service.check(body(same))).plan().fingerprint();

        assertThat(service.apply(ACTOR, ORIGIN, body(same), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.NO_CHANGE));

        String yamlFingerprint = fingerprint();
        when(store.lockRole(7L)).thenReturn(new RoleStoreOutcome.RoleMissing<>());
        assertThat(service.apply(ACTOR, ORIGIN, body(YAML), yamlFingerprint))
                .as("排他の前に消えたロールは指紋が合っても置き換えられない")
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.TRANSFER_STALE));

        when(store.lockRole(7L)).thenReturn(new RoleStoreOutcome.Done<>(sales));
        when(activeDsl.current())
                .thenReturn(
                        new ActiveDsl.Present(RoleDslFixture.sample(), RoleDslFixture.HASH), new ActiveDsl.Absent());
        assertThat(service.apply(ACTOR, ORIGIN, body(YAML), yamlFingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.DSL_NOT_APPLIED));
        assertThat(events)
                .extracting(RoleAuditEvent::failure)
                .containsExactly(
                        RoleAuditFailure.NO_CHANGE,
                        RoleAuditFailure.ROLE_TRANSFER_STALE,
                        RoleAuditFailure.DSL_NOT_APPLIED);
        verify(store, never()).applyTransfer(any(), any());
    }

    @Test
    @DisplayName(
            "a name key violation becomes ROLE_NAME_DUPLICATE with an audit, and a lock timeout is busy without one")
    void storeOutcomes() {
        String fingerprint = fingerprint();
        when(store.applyTransfer(any(), any())).thenReturn(new RoleStoreOutcome.NameTaken<>());

        assertThat(service.apply(ACTOR, ORIGIN, body(YAML), fingerprint))
                .isEqualTo(new TransferApplyResult.Rejected(RoleRejection.NAME_DUPLICATE));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.failure()).isEqualTo(RoleAuditFailure.ROLE_NAME_DUPLICATE);
            assertThat(((RoleAuditDetail.Transfer) event.detail()).roles()).hasSize(2);
        });

        when(store.lockRole(7L)).thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_ROW));
        assertThat(service.apply(ACTOR, ORIGIN, body(YAML), fingerprint)).isInstanceOf(TransferApplyResult.Busy.class);
        assertThat(events).hasSize(1);

        when(store.lockRole(7L)).thenReturn(new RoleStoreOutcome.Done<>(sales));
        when(store.applyTransfer(any(), any())).thenReturn(new RoleStoreOutcome.AlreadyAssigned<>());
        assertThatThrownBy(() -> service.apply(ACTOR, ORIGIN, body(YAML), fingerprint))
                .isInstanceOf(IllegalStateException.class);
        verify(slot, org.mockito.Mockito.atLeast(4)).release();
    }

    @Test
    @DisplayName(
            "the slot is released when reading the body fails, and a malformed fingerprint is refused before the slot")
    void releaseOnFailure() throws IOException {
        InputStream broken = mock(InputStream.class);
        when(broken.readNBytes(org.mockito.ArgumentMatchers.anyInt())).thenThrow(new IOException("closed"));

        assertThatThrownBy(() -> service.check(new RoleTransferBody(broken))).isInstanceOf(UncheckedIOException.class);
        verify(slot).release();
        assertThatThrownBy(() -> service.apply(ACTOR, ORIGIN, body(YAML), "ABC"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(slot).tryAcquire();
        verify(store, never()).lockRole(anyLong());
    }

    @Test
    @DisplayName("the export writes every role in id order with its settings and flags a body over the import limit")
    void export() {
        when(settings.findAllOrderByRole())
                .thenReturn(List.of(new RoleSettingRow(7, "SALES", "", "", MainPermission.READ, true, null)));

        TransferExport small = service.export();
        TransferExport large = service(new RoleTransferLimits(10)).export();

        assertThat(new String(small.yaml().bytes(), StandardCharsets.UTF_8))
                .isEqualTo("version: 1\nroles:\n  Sales:\n    schemas:\n      SALES:\n        main: READ\n"
                        + "        create: true\n  Other: {}\n");
        assertThat(small.exceedsImportLimit()).isFalse();
        assertThat(large.exceedsImportLimit()).isTrue();
        assertThat(small.toString()).doesNotContain("Sales");
        verifyNoInteractions(slot, publisher, store);
    }
}
