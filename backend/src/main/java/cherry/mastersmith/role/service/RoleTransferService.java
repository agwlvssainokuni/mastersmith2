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

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dsl.service.SafeYamlReader;
import cherry.mastersmith.dsl.service.SafeYamlRejectionKind;
import cherry.mastersmith.dsl.service.SafeYamlResult;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionTreeBuilder;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.domain.TransferAction;
import cherry.mastersmith.role.repository.PermissionSettingRepository;
import cherry.mastersmith.role.repository.RoleKeyView;
import cherry.mastersmith.role.repository.RoleRepository;
import cherry.mastersmith.role.repository.RoleSettingRow;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.role.store.TransferWrite;
import cherry.mastersmith.role.store.TransferWritten;
import cherry.mastersmith.role.transfer.RoleTransferDocument;
import cherry.mastersmith.role.transfer.RoleTransferPayload;
import cherry.mastersmith.role.transfer.RoleTransferReader;
import cherry.mastersmith.role.transfer.RoleTransferTree;
import cherry.mastersmith.role.transfer.RoleTransferWriter;
import cherry.mastersmith.role.transfer.TransferErrors;
import cherry.mastersmith.role.transfer.TransferFingerprint;
import cherry.mastersmith.role.transfer.TransferPlan;
import cherry.mastersmith.role.transfer.TransferPlanner;
import cherry.mastersmith.role.transfer.TransferPlanner.CurrentRole;
import cherry.mastersmith.role.transfer.TransferRolePlan;
import cherry.mastersmith.role.transfer.TransferRoleSettings;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 権限の YAML の書き出し・確かめ・適用の業務処理（Intent 261004-role-menu の U4、{@code logical-components.md} の L3、FS の 2.13〜2.15、
 * BR9・BR11.7、{@code reliability-design.md} 2.5・2.6）。
 *
 * <p>確かめと適用の判定の順（NFR3.8）: 本文の型・大きさ（画面入出力の層と既存のフィルター）→ 入口（{@link RoleTransferSlot}。取れなければ
 * {@code ROLE_BUSY} と WARN）→ DSL の有無 → 本文を上限まで読む → 上限つきの読み込み → 中身の検証 →（適用だけ）置き換えるロールの行の
 * 排他（ID の昇順）→ 一覧の作り直し → DSL の有無（もう一度）→ 指紋 → 変える点の有無。入口は {@code finally} で必ず放す。
 *
 * <ul>
 *   <li>書き出しは読み取りだけのトランザクション1つで、入口に入らない（受け入れた制約）。監査に残さない
 *   <li>確かめは読み取りだけのトランザクションで一覧と指紋を作り、保存しない。監査に残さない（BR9.15・BR11.3）
 *   <li>適用は1つ目のトランザクション（{@link RoleStoreTransactions}）で、排他・作り直し・書き込みをまとめて確定し、成功の出来事を1件出す。
 *       業務の拒否（指紋の不一致・DSL が無い・変える点が無い・名前の重なり）は巻き戻した後に2つ目のトランザクションで失敗の出来事を出す
 *       （計画の 4.3）。排他・鍵の待ちの上限切れと、読み込みの拒否・中身の誤り・大きさの超過は監査に残さない
 * </ul>
 *
 * <p>本文・木・一覧・誤りは中身を伏せる型で受け渡し、業務のログは出さない（NFR1.9）。
 */
@Service
public class RoleTransferService {

    private final RoleStoreTransactions transactions;

    private final RoleRepository roles;

    private final PermissionSettingRepository settings;

    private final ActiveDslModelProvider activeDsl;

    private final SafeYamlReader yamlReader;

    private final RoleTransferSlot slot;

    private final RoleTransferLimits limits;

    private final RoleBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate failureTransaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param transactions store を呼ぶ1つ目のトランザクションの入口
     * @param roles ロールの表の読み取り
     * @param settings 権限の設定の表の読み取り
     * @param activeDsl 適用中の DSL の提供口
     * @param yamlReader 上限つきの YAML の読み込み
     * @param slot 確かめと適用の入口
     * @param limits 上限
     * @param barrier 待ち合わせの口
     * @param eventPublisher 監査の出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public RoleTransferService(
            RoleStoreTransactions transactions,
            RoleRepository roles,
            PermissionSettingRepository settings,
            ActiveDslModelProvider activeDsl,
            SafeYamlReader yamlReader,
            RoleTransferSlot slot,
            RoleTransferLimits limits,
            RoleBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
        this.roles = roles;
        this.settings = settings;
        this.activeDsl = activeDsl;
        this.yamlReader = yamlReader;
        this.slot = slot;
        this.limits = limits;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * 権限の YAML を書き出す（FS の 2.13、BR9.14・NFR2.9）。DSL の有無によらず、今ある設定を書く（BR9.13）。
     *
     * @return 書き出した本文と、読み込みの上限を超えたか
     */
    public TransferExport export() {
        String yaml = Objects.requireNonNull(readOnly.execute(status -> {
            Map<Long, Map<PermissionTarget, PermissionValues>> byRole = new HashMap<>();
            for (RoleSettingRow row : settings.findAllOrderByRole()) {
                byRole.computeIfAbsent(row.roleId(), id -> new LinkedHashMap<>())
                        .put(row.target(), row.values());
            }
            List<TransferRoleSettings> exported = roles.findAllKeys().stream()
                    .map(role -> new TransferRoleSettings(role.name(), byRole.getOrDefault(role.roleId(), Map.of())))
                    .toList();
            return RoleTransferWriter.write(exported);
        }));
        RoleTransferPayload payload = new RoleTransferPayload(yaml.getBytes(StandardCharsets.UTF_8));
        return new TransferExport(payload, payload.size() > limits.maxBytes());
    }

    /**
     * 権限の YAML を確かめる（FS の 2.14、BR9.3〜BR9.9・BR9.13・BR9.15）。設定を変えず、指紋を保存しない。
     *
     * @param body 要求の本文の流れ
     * @return 結果
     */
    public TransferCheckResult check(RoleTransferBody body) {
        Objects.requireNonNull(body, "body");
        if (!slot.tryAcquire()) {
            return new TransferCheckResult.Busy();
        }
        try {
            if (activeDsl.current() instanceof ActiveDsl.Absent) {
                return new TransferCheckResult.DslNotApplied();
            }
            RoleTransferPayload payload = readBody(body);
            if (payload == null) {
                return new TransferCheckResult.TooLarge();
            }
            return switch (parse(payload)) {
                case Parsed.TooLarge _ -> new TransferCheckResult.TooLarge();
                case Parsed.Invalid(TransferErrors errors) -> new TransferCheckResult.Invalid(errors);
                case Parsed.Valid(RoleTransferDocument document) -> {
                    TransferPlan plan = readOnly.execute(status -> planFor(document, activeDsl.current()));
                    yield plan == null
                            ? new TransferCheckResult.DslNotApplied()
                            : new TransferCheckResult.Checked(plan);
                }
            };
        } finally {
            slot.release();
        }
    }

    /**
     * 権限の YAML を適用する（FS の 2.15、BR9.6・BR9.7・BR9.10〜BR9.13、BR11.7）。
     *
     * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
     * @param origin 要求の送り手の情報
     * @param body 要求の本文の流れ
     * @param fingerprint 確かめの指紋（要求のヘッダーの値。16 進数の小文字 64 文字であることを画面入出力の層が確かめ済み）
     * @return 結果
     */
    public TransferApplyResult apply(
            long actorUserId, RequestOrigin origin, RoleTransferBody body, String fingerprint) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(body, "body");
        if (!TransferFingerprint.isWellFormed(fingerprint)) {
            throw new IllegalArgumentException("指紋は 16 進数の小文字 64 文字です");
        }
        if (!slot.tryAcquire()) {
            return new TransferApplyResult.Busy();
        }
        try {
            if (activeDsl.current() instanceof ActiveDsl.Absent) {
                return rejected(
                        actorUserId,
                        origin,
                        RoleRejection.DSL_NOT_APPLIED,
                        new RoleAuditDetail.Transfer(null, fingerprint, List.of()));
            }
            RoleTransferPayload payload = readBody(body);
            if (payload == null) {
                return new TransferApplyResult.TooLarge();
            }
            return switch (parse(payload)) {
                case Parsed.TooLarge _ -> new TransferApplyResult.TooLarge();
                case Parsed.Invalid(TransferErrors errors) -> new TransferApplyResult.Invalid(errors);
                case Parsed.Valid(RoleTransferDocument document) ->
                    applyDocument(actorUserId, origin, document, payload.sha256(), fingerprint);
            };
        } finally {
            slot.release();
        }
    }

    private TransferApplyResult applyDocument(
            long actorUserId,
            RequestOrigin origin,
            RoleTransferDocument document,
            String fileSha256,
            String fingerprint) {
        RoleFirstStep<TransferWritten> step = transactions.inFirst(store -> {
            Map<Long, Role> locked = new HashMap<>();
            for (long roleId : matchedRoleIds(document, roles.findAllKeys())) {
                barrier.beforeLock(RoleOperation.APPLY_TRANSFER, roleId);
                RoleStoreOutcome<Role> lock = store.lockRole(roleId);
                if (lock instanceof RoleStoreOutcome.Done<Role>(Role role)) {
                    locked.put(roleId, role);
                    barrier.afterLock(RoleOperation.APPLY_TRANSFER, roleId);
                } else if (!(lock instanceof RoleStoreOutcome.RoleMissing<Role>)) {
                    return new RoleFirstStep.Store<>(lock, null);
                }
            }
            TransferPlan plan = planFor(document, activeDsl.current());
            if (plan == null) {
                return new RoleFirstStep.Rejected<>(
                        RoleRejection.DSL_NOT_APPLIED,
                        new RoleAuditDetail.Transfer(fileSha256, fingerprint, List.of()));
            }
            RoleAuditDetail.Transfer detail = plan.toAuditDetail(fileSha256, fingerprint);
            if (!plan.fingerprint().equals(fingerprint) || !locked.keySet().containsAll(plan.replacedRoleIds())) {
                return new RoleFirstStep.Rejected<>(RoleRejection.TRANSFER_STALE, detail);
            }
            if (plan.isEmpty()) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, detail);
            }
            RoleStoreOutcome<TransferWritten> outcome =
                    store.applyTransfer(writesOf(plan, document, locked), clock.instant());
            if (!(outcome instanceof RoleStoreOutcome.Done<TransferWritten>(TransferWritten written))) {
                return new RoleFirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(RoleOperation.APPLY_TRANSFER, fingerprint.substring(0, 8));
            eventPublisher.publishEvent(
                    RoleAuditEvent.transferApplied(actorUserId, null, detail, clock.instant(), origin));
            return new RoleFirstStep.Done<>(written);
        });
        return switch (step) {
            case RoleFirstStep.Done<TransferWritten>(TransferWritten written) ->
                new TransferApplyResult.Applied(written.created(), written.replaced());
            case RoleFirstStep.Rejected<TransferWritten>(RoleRejection rejection, RoleAuditDetail detail) ->
                rejected(actorUserId, origin, rejection, (RoleAuditDetail.Transfer) detail);
            case RoleFirstStep.Store<TransferWritten>(RoleStoreOutcome<?> outcome, RoleAuditDetail detail) ->
                switch (outcome) {
                    case RoleStoreOutcome.Busy<?> _ -> new TransferApplyResult.Busy();
                    case RoleStoreOutcome.NameTaken<?> _ ->
                        rejected(actorUserId, origin, RoleRejection.NAME_DUPLICATE, (RoleAuditDetail.Transfer) detail);
                    default ->
                        throw new IllegalStateException(RoleOperation.APPLY_TRANSFER + " の結果として想定外です: " + outcome);
                };
        };
    }

    /** 1つ目を巻き戻した後に（または1つ目を始める前に）、2つ目のトランザクションで失敗の出来事だけを出す。 */
    private TransferApplyResult rejected(
            long actorUserId, RequestOrigin origin, RoleRejection rejection, RoleAuditDetail.Transfer detail) {
        RoleAuditEvent event =
                RoleAuditEvent.transferApplied(actorUserId, rejection.auditFailure(), detail, clock.instant(), origin);
        failureTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        return new TransferApplyResult.Rejected(rejection);
    }

    /** 置き換えに当たりうるロール（ファイルの名前の鍵に当たる今のロール）の ID を昇順で返す。 */
    private static List<Long> matchedRoleIds(RoleTransferDocument document, List<RoleKeyView> current) {
        Set<String> keys =
                document.roles().stream().map(role -> role.name().key()).collect(Collectors.toSet());
        return current.stream()
                .filter(role -> keys.contains(role.nameKey()))
                .map(RoleKeyView::roleId)
                .sorted()
                .toList();
    }

    /** 今のロールと設定を読み、変わる点の一覧を作る（DSL が無ければ null）。呼び出し元のトランザクションの中で使う。 */
    private TransferPlan planFor(RoleTransferDocument document, ActiveDsl dsl) {
        if (!(dsl instanceof ActiveDsl.Present(var model, String dslHash))) {
            return null;
        }
        List<RoleKeyView> current = roles.findAllKeys();
        List<Long> matched = matchedRoleIds(document, current);
        Map<Long, Map<PermissionTarget, PermissionValues>> byRole = new HashMap<>();
        if (!matched.isEmpty()) {
            for (RoleSettingRow row : settings.findAllOfRoles(matched)) {
                byRole.computeIfAbsent(row.roleId(), id -> new HashMap<>()).put(row.target(), row.values());
            }
        }
        List<CurrentRole> currentRoles = current.stream()
                .map(role -> new CurrentRole(
                        role.roleId(), role.name(), role.nameKey(), byRole.getOrDefault(role.roleId(), Map.of())))
                .toList();
        return TransferPlanner.plan(
                document, currentRoles, target -> PermissionTreeBuilder.inCurrentDsl(model, target), dslHash);
    }

    /** 変わる点の一覧とファイルの中身から、書くロールを並べる（ロールの鍵の順）。 */
    private static List<TransferWrite> writesOf(
            TransferPlan plan, RoleTransferDocument document, Map<Long, Role> locked) {
        Map<String, Map<PermissionTarget, PermissionValues>> fileSettings = new HashMap<>();
        for (RoleTransferDocument.Role role : document.roles()) {
            fileSettings.put(role.name().key(), role.settings());
        }
        List<TransferWrite> writes = new ArrayList<>();
        for (TransferRolePlan role : plan.roles()) {
            Map<PermissionTarget, PermissionValues> after =
                    fileSettings.get(role.fileName().key());
            writes.add(
                    role.action() == TransferAction.CREATE
                            ? TransferWrite.create(role.fileName(), after)
                            : TransferWrite.replace(locked.get(role.roleId()), after));
        }
        return writes;
    }

    /** 本文を上限まで読む（上限を超えたら null。分割送信の本文の大きさの上限）。 */
    private RoleTransferPayload readBody(RoleTransferBody body) {
        try {
            byte[] bytes = body.stream().readNBytes(limits.maxBytes() + 1);
            return bytes.length > limits.maxBytes() ? null : new RoleTransferPayload(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("権限の YAML の本文を読めませんでした", e);
        }
    }

    /** 上限つきで読み、中身を検証する。 */
    private Parsed parse(RoleTransferPayload payload) {
        return switch (yamlReader.read(payload.bytes(), limits.toSafeYamlLimits())) {
            case SafeYamlResult.Rejected rejected
            when rejected.kind() == SafeYamlRejectionKind.TOO_LARGE -> new Parsed.TooLarge();
            case SafeYamlResult.Rejected rejected -> new Parsed.Invalid(RoleTransferReader.rejected(rejected));
            case SafeYamlResult.Parsed(var tree, var positions) ->
                switch (RoleTransferReader.read(new RoleTransferTree(tree, positions))) {
                    case RoleTransferReader.Valid(RoleTransferDocument document) -> new Parsed.Valid(document);
                    case RoleTransferReader.Invalid(TransferErrors errors) -> new Parsed.Invalid(errors);
                };
        };
    }

    /** 読み込みと検証の結果（このクラスの中だけで使う）。 */
    private sealed interface Parsed {

        /** 大きさの上限を超えた。 */
        record TooLarge() implements Parsed {}

        /**
         * 読み込みの拒否か中身の誤り。
         *
         * @param errors 誤りの一覧
         */
        record Invalid(TransferErrors errors) implements Parsed {}

        /**
         * 検証を通った。
         *
         * @param document 中身
         */
        record Valid(RoleTransferDocument document) implements Parsed {}
    }
}
