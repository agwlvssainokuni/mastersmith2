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

import cherry.mastersmith.common.paging.Paging;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.role.domain.PermissionNode;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionTreeBuilder;
import cherry.mastersmith.role.domain.PermissionTreeBuilder.StoredChild;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.repository.AssignmentCount;
import cherry.mastersmith.role.repository.PermissionLevelRow;
import cherry.mastersmith.role.repository.PermissionSettingRepository;
import cherry.mastersmith.role.repository.PermissionSettingRow;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.role.repository.RoleRepository;
import cherry.mastersmith.role.repository.RoleRowView;
import cherry.mastersmith.role.repository.RoleView;
import cherry.mastersmith.role.store.Referent;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ロールの管理と権限の設定の業務処理（Intent 261004-role-menu の U4、{@code logical-components.md} の L3、FS の 2.1〜2.7）。
 *
 * <p>このクラスには {@code @Transactional} を付けず、トランザクションを {@link TransactionTemplate} で作る。変える操作は、store を呼ぶ
 * 1つ目のトランザクションを {@link RoleStoreTransactions} で組み、入力の判定 → ロールの行の排他 → 待ち合わせの口 → 業務の判定 → 書き込みと
 * flush → 成功の出来事の順に進む（BR8.1）。排他の前に書き込みは無い。
 *
 * <ul>
 *   <li>成功の出来事は1つ目のトランザクションの中で出し、確定の後に {@code audit} が記録する（BR11.1）
 *   <li>業務の拒否（書き込みの前の判定と、違反の読み替え）は、1つ目を巻き戻した後に、2つ目の {@link TransactionTemplate}（書き込み
 *       なし）で失敗の出来事だけを出す（BR8.5・BR11.2、計画の 4.3・13節 Q2: A）
 *   <li>待ちの上限切れ（{@code ROLE_BUSY}）・入力の誤り・読み取りは出来事を出さない（BR8.3・BR11.3）
 * </ul>
 *
 * <p>入れ子の REQUIRES_NEW は使わない。一覧・1件・木は読み取りだけの1つのトランザクションで読む。適用中の DSL は
 * {@link ActiveDslModelProvider} で読み（保存では排他の後に読む）、業務のログは出さない（上限切れの WARN は store が出す）。操作した人の
 * 管理者の印は、要求の入口の決まりだけで確かめる（BR2.1）。
 */
@Service
public class RoleAdminService {

    private final RoleStoreTransactions transactions;

    private final RoleRepository roles;

    private final PermissionSettingRepository settings;

    private final RoleAssignmentRepository assignments;

    private final ActiveDslModelProvider activeDsl;

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
     * @param assignments 割り当ての表の読み取り（削除の確かめと一覧の数。B5）
     * @param activeDsl 適用中の DSL の提供口
     * @param barrier 待ち合わせの口
     * @param eventPublisher 監査の出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public RoleAdminService(
            RoleStoreTransactions transactions,
            RoleRepository roles,
            PermissionSettingRepository settings,
            RoleAssignmentRepository assignments,
            ActiveDslModelProvider activeDsl,
            RoleBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
        this.roles = roles;
        this.settings = settings;
        this.assignments = assignments;
        this.activeDsl = activeDsl;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * ロールを作る（FS の 2.1、BR1.1〜BR1.4・BR3.1・BR8.4・BR8.5）。
     *
     * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
     * @param origin 要求の送り手の情報
     * @param rawName 要求の名前（正規化の前の値）
     * @return 結果
     */
    public RoleCreateResult create(long actorUserId, RequestOrigin origin, String rawName) {
        Objects.requireNonNull(origin, "origin");
        if (!(RoleName.parse(rawName) instanceof RoleNameValidation.Valid(RoleName name))) {
            return new RoleCreateResult.InvalidName(invalidReason(rawName));
        }
        RoleAuditDetail detail = new RoleAuditDetail.Name(name.value());
        RoleFirstStep<RoleDetail> step = transactions.inFirst(store -> {
            if (roles.findIdByNameKey(name.key()).isPresent()) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NAME_DUPLICATE, detail);
            }
            barrier.afterCheck(RoleOperation.CREATE, name.key());
            RoleStoreOutcome<Role> outcome = store.insertRole(name, clock.instant());
            if (!(outcome instanceof RoleStoreOutcome.Done<Role>(Role role))) {
                return new RoleFirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(RoleOperation.CREATE, name.key());
            eventPublisher.publishEvent(RoleAuditEvent.succeeded(
                    RoleOperation.CREATE, actorUserId, role.getRoleId(), detail, clock.instant(), origin));
            return new RoleFirstStep.Done<>(
                    new RoleDetail(role.getRoleId(), role.getName(), role.getCreatedAt(), role.getUpdatedAt()));
        });
        return switch (step) {
            case RoleFirstStep.Done<RoleDetail>(RoleDetail role) -> new RoleCreateResult.Created(role);
            case RoleFirstStep.Rejected<RoleDetail>(RoleRejection rejection, RoleAuditDetail rejected) -> {
                publishFailure(RoleOperation.CREATE, actorUserId, null, rejection, rejected, origin);
                yield new RoleCreateResult.Rejected(rejection);
            }
            case RoleFirstStep.Store<RoleDetail>(RoleStoreOutcome<?> outcome, RoleAuditDetail violated) ->
                switch (outcome) {
                    case RoleStoreOutcome.Busy<?> _ -> new RoleCreateResult.Busy();
                    case RoleStoreOutcome.NameTaken<?> _ -> {
                        publishFailure(
                                RoleOperation.CREATE,
                                actorUserId,
                                null,
                                RoleRejection.NAME_DUPLICATE,
                                violated,
                                origin);
                        yield new RoleCreateResult.Rejected(RoleRejection.NAME_DUPLICATE);
                    }
                    default -> throw unexpected(RoleOperation.CREATE, outcome);
                };
        };
    }

    /**
     * ロールの名前を変える（FS の 2.2、BR1.4・BR1.5・BR3.6・BR8.1・BR8.3〜BR8.5）。判定の順は入力 → ロールの有無 → 変えるものが無い →
     * 名前の重なり（BR3.6）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま。0 以下は無いロール）
     * @param rawName 要求の名前（正規化の前の値）
     * @return 結果
     */
    public RoleChangeResult rename(long actorUserId, RequestOrigin origin, long roleId, String rawName) {
        Objects.requireNonNull(origin, "origin");
        if (!(RoleName.parse(rawName) instanceof RoleNameValidation.Valid(RoleName name))) {
            return new RoleChangeResult.InvalidName(invalidReason(rawName));
        }
        RoleOperation operation = RoleOperation.RENAME;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            String before = role.getName();
            if (name.sameAs(before)) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, new RoleAuditDetail.Name(before));
            }
            RoleAuditDetail detail = new RoleAuditDetail.Rename(before, name.value());
            Optional<Long> sameKey = roles.findIdByNameKey(name.key());
            if (sameKey.isPresent() && sameKey.get() != roleId) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NAME_DUPLICATE, detail);
            }
            barrier.afterCheck(operation, name.key());
            RoleStoreOutcome<Role> outcome = store.renameRole(role, name, clock.instant());
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, name.key());
            return done(operation, actorUserId, roleId, detail, origin);
        }));
        return changeResult(step, operation, actorUserId, roleId, origin);
    }

    /**
     * ロールを消す（FS の 2.3、BR3.2〜BR3.4・BR8.1）。ロールの行を排他した後に、利用者への直接の割り当ての数とグループへの割り当ての数を
     * 数え、どちらかが 1 以上なら {@code ROLE_IN_USE}（応答と detail に数）で断る。通れば、権限の設定 → そのロールを指す作業ロールの保存 →
     * ロールの順に消す（store）。割り当ての側も同じロールの行を先に排他するため、削除と割り当ての重なりはどちらかが先に確定する（AC1.1.6）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま）
     * @return 結果
     */
    public RoleChangeResult delete(long actorUserId, RequestOrigin origin, long roleId) {
        Objects.requireNonNull(origin, "origin");
        RoleOperation operation = RoleOperation.DELETE;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            long users = assignments.countUsersOfRole(roleId);
            long groups = assignments.countGroupsOfRole(roleId);
            if (users > 0 || groups > 0) {
                return new RoleFirstStep.Rejected<>(
                        RoleRejection.IN_USE,
                        new RoleAuditDetail.InUse(role.getName(), Math.toIntExact(users), Math.toIntExact(groups)));
            }
            RoleAuditDetail detail = new RoleAuditDetail.Name(role.getName());
            RoleStoreOutcome<Void> outcome = store.deleteRole(role);
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, String.valueOf(roleId));
            return done(operation, actorUserId, roleId, detail, origin);
        }));
        return changeResult(step, operation, actorUserId, roleId, origin);
    }

    /**
     * 権限を保存する（FS の 2.6、BR4.2・BR4.4〜BR4.6・BR4.8・BR4.9・BR8.1・BR11.6）。判定の順は排他 → 待ち合わせの口 → DSL が無い →
     * 今の DSL に無い対象への値の設定 → 変わる対象が無い。載せた対象だけを置き換える差分の保存で、変わった対象の前後の値を監査に残す。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま）
     * @param command 判定した保存の要求
     * @return 結果
     */
    public RoleChangeResult savePermissions(
            long actorUserId, RequestOrigin origin, long roleId, PermissionInputs.SaveCommand command) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(command, "command");
        RoleOperation operation = RoleOperation.CHANGE_PERMISSIONS;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            RoleAuditDetail named = new RoleAuditDetail.Name(role.getName());
            if (!(activeDsl.current() instanceof ActiveDsl.Present(DslModel dsl, String _))) {
                return new RoleFirstStep.Rejected<>(RoleRejection.DSL_NOT_APPLIED, named);
            }
            boolean settingOutsideDsl = command.entries().entrySet().stream()
                    .anyMatch(entry ->
                            !entry.getValue().isEmpty() && !PermissionTreeBuilder.inCurrentDsl(dsl, entry.getKey()));
            if (settingOutsideDsl) {
                return new RoleFirstStep.Rejected<>(RoleRejection.TARGET_NOT_IN_DSL, named);
            }
            PermissionTarget scope = command.scope();
            Map<PermissionTarget, PermissionValues> current = new LinkedHashMap<>();
            for (PermissionSettingRow row : settings.findInScope(roleId, scope.schemaName(), scope.storedTableName())) {
                current.put(row.target(), row.values());
            }
            List<RoleAuditDetail.PermissionChange> changes = new ArrayList<>();
            Map<PermissionTarget, PermissionValues> afters = new LinkedHashMap<>();
            command.entries().forEach((target, after) -> {
                PermissionValues before = current.getOrDefault(target, PermissionValues.NOT_SET);
                if (!before.equals(after)) {
                    changes.add(new RoleAuditDetail.PermissionChange(target, before, after));
                    afters.put(target, after);
                }
            });
            if (changes.isEmpty()) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, named);
            }
            RoleStoreOutcome<Void> outcome = store.writePermissions(role, afters, clock.instant());
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, named);
            }
            barrier.afterWrite(operation, String.valueOf(roleId));
            return done(
                    operation,
                    actorUserId,
                    roleId,
                    new RoleAuditDetail.PermissionChanges(role.getName(), changes),
                    origin);
        }));
        return changeResult(step, operation, actorUserId, roleId, origin);
    }

    /**
     * 今の DSL に無い対象の設定を消す（FS の 2.7、BR4.7。Should）。DSL の有無によらず受け付け、今の DSL にある対象を指したら入力の誤り。
     * 消す行が1つも無ければ変えるものが無い。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま）
     * @param command 判定した消す操作の要求
     * @return 結果
     */
    public RoleChangeResult clearPermissions(
            long actorUserId, RequestOrigin origin, long roleId, PermissionInputs.ClearCommand command) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(command, "command");
        if (activeDsl.current() instanceof ActiveDsl.Present(DslModel dsl, String _)) {
            for (int i = 0; i < command.targets().size(); i++) {
                if (PermissionTreeBuilder.inCurrentDsl(dsl, command.targets().get(i))) {
                    return new RoleChangeResult.InvalidInput("targets[" + i + "]", FieldErrorReason.INVALID_VALUE);
                }
            }
        }
        RoleOperation operation = RoleOperation.CHANGE_PERMISSIONS;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            RoleAuditDetail named = new RoleAuditDetail.Name(role.getName());
            List<RoleAuditDetail.PermissionChange> changes = new ArrayList<>();
            List<PermissionTarget> existing = new ArrayList<>();
            for (PermissionTarget target : command.targets()) {
                settings.findRow(roleId, target.schemaName(), target.storedTableName(), target.storedColumnName())
                        .ifPresent(row -> {
                            existing.add(target);
                            changes.add(new RoleAuditDetail.PermissionChange(
                                    target, row.values(), PermissionValues.NOT_SET));
                        });
            }
            if (existing.isEmpty()) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, named);
            }
            RoleStoreOutcome<Integer> outcome = store.clearPermissions(role, existing, clock.instant());
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, named);
            }
            barrier.afterWrite(operation, String.valueOf(roleId));
            return done(
                    operation,
                    actorUserId,
                    roleId,
                    new RoleAuditDetail.PermissionChanges(role.getName(), changes),
                    origin);
        }));
        return changeResult(step, operation, actorUserId, roleId, origin);
    }

    /**
     * ロールの一覧の1ページを読む（FS の 2.4、BR3.4・BR3.5）。監査の出来事は出さない。
     *
     * @param rawPage 要求の page（無ければ null）
     * @return 結果
     */
    public RoleListResult list(String rawPage) {
        OptionalInt parsed = Paging.parsePage(rawPage);
        if (parsed.isEmpty()) {
            return new RoleListResult.InvalidPage();
        }
        int page = parsed.getAsInt();
        RoleListResult.Page result = readOnly.execute(status -> readPage(page));
        return new RoleListResult.Listed(Objects.requireNonNull(result, "result"));
    }

    /**
     * ロール1件を読む（FS の 2.4a、BR3.7）。監査の出来事は出さない。
     *
     * @param roleId ロールの ID（要求の値のまま）
     * @return 結果
     */
    public RoleDetailResult detail(long roleId) {
        Optional<RoleView> view = Objects.requireNonNull(readOnly.execute(status -> roles.findView(roleId)), "result");
        return view.<RoleDetailResult>map(found -> new RoleDetailResult.Found(
                        new RoleDetail(found.roleId(), found.name(), found.createdAt(), found.updatedAt())))
                .orElseGet(RoleDetailResult.NotFound::new);
    }

    /**
     * 権限の設定の木の1段目（スキーマ）を読む（FS の 2.5、BR4.5・BR4.10・BR4.11）。
     *
     * @param roleId ロールの ID（要求の値のまま）
     * @return 結果
     */
    public PermissionTreeResult schemas(long roleId) {
        return readTree(roleId, dsl -> {
            Map<String, StoredChild> stored = new LinkedHashMap<>();
            for (PermissionLevelRow row : settings.findSchemaLevel(roleId)) {
                stored.put(row.name(), new StoredChild(row.values(), row.deeperRows()));
            }
            return PermissionTreeBuilder.schemas(dsl, stored);
        });
    }

    /**
     * スキーマの下のテーブルを読む（FS の 2.5、BR4.10〜BR4.12）。
     *
     * @param roleId ロールの ID（要求の値のまま）
     * @param schemaName スキーマの名前（問い合わせの引数のまま。長さで拒否しない）
     * @return 結果
     */
    public PermissionTreeResult tables(long roleId, String schemaName) {
        Objects.requireNonNull(schemaName, "schemaName");
        return readTree(roleId, dsl -> {
            PermissionValues schemaValues = PermissionValues.NOT_SET;
            Map<String, StoredChild> stored = new LinkedHashMap<>();
            for (PermissionLevelRow row : settings.findTableLevel(roleId, schemaName)) {
                if (row.name().isEmpty()) {
                    schemaValues = row.values();
                } else {
                    stored.put(row.name(), new StoredChild(row.values(), row.deeperRows()));
                }
            }
            return PermissionTreeBuilder.tables(dsl, schemaName, schemaValues, stored);
        });
    }

    /**
     * テーブルの下のカラムを読む（FS の 2.5、BR4.10〜BR4.12）。今の DSL に無いテーブルでも、設定があればそのカラムを返す。
     *
     * @param roleId ロールの ID（要求の値のまま）
     * @param schemaName スキーマの名前（問い合わせの引数のまま）
     * @param tableName テーブルの名前（問い合わせの引数のまま）
     * @return 結果
     */
    public PermissionTreeResult columns(long roleId, String schemaName, String tableName) {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(tableName, "tableName");
        return readTree(roleId, dsl -> {
            PermissionValues schemaValues = PermissionValues.NOT_SET;
            PermissionValues tableValues = PermissionValues.NOT_SET;
            Map<String, StoredChild> stored = new LinkedHashMap<>();
            for (PermissionSettingRow row : settings.findColumnLevel(roleId, schemaName, tableName)) {
                if (row.tableName().isEmpty()) {
                    schemaValues = row.values();
                } else if (row.columnName().isEmpty()) {
                    tableValues = row.values();
                } else {
                    stored.put(row.columnName(), new StoredChild(row.values(), 0));
                }
            }
            return PermissionTreeBuilder.columns(dsl, schemaName, tableName, schemaValues, tableValues, stored);
        });
    }

    /** ロールの有無と DSL を確かめてから、読み取りだけの1つのトランザクションで木の1階層を作る。 */
    private PermissionTreeResult readTree(long roleId, Function<DslModel, List<PermissionNode>> build) {
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    if (!roles.existsRole(roleId)) {
                        return new PermissionTreeResult.RoleNotFound();
                    }
                    if (!(activeDsl.current() instanceof ActiveDsl.Present(DslModel dsl, String _))) {
                        return new PermissionTreeResult.DslNotApplied();
                    }
                    return new PermissionTreeResult.Found(build.apply(dsl));
                }),
                "result");
    }

    /** ロールの行を排他し、取れたら処理を続ける。行が無ければ拒否、上限切れは store の結果のまま返す。 */
    private RoleFirstStep<Void> withLockedRole(
            RoleStore store, RoleOperation operation, long roleId, Function<Role, RoleFirstStep<Void>> work) {
        barrier.beforeLock(operation, roleId);
        RoleStoreOutcome<Role> lock = store.lockRole(roleId);
        return switch (lock) {
            case RoleStoreOutcome.Done<Role>(Role role) -> {
                barrier.afterLock(operation, roleId);
                yield work.apply(role);
            }
            case RoleStoreOutcome.RoleMissing<Role> _ ->
                new RoleFirstStep.Rejected<>(RoleRejection.ROLE_NOT_FOUND, null);
            default -> new RoleFirstStep.Store<>(lock, null);
        };
    }

    /** 成功の出来事を1つ目のトランザクションの中で出し、成功の結果を返す。 */
    private RoleFirstStep<Void> done(
            RoleOperation operation, long actorUserId, long roleId, RoleAuditDetail detail, RequestOrigin origin) {
        eventPublisher.publishEvent(
                RoleAuditEvent.succeeded(operation, actorUserId, roleId, detail, clock.instant(), origin));
        return new RoleFirstStep.Done<>(null);
    }

    /**
     * 1つ目の結果を、変える操作の結果に変える。拒否と違反の読み替えは2つ目のトランザクションで失敗の出来事を出す（計画の 4.3）。ロールへの
     * 外部キーの違反は、削除なら {@code ROLE_IN_USE}（残りの数は2つ目のトランザクションで数え直す）、権限の書き込みなら
     * {@code ROLE_NOT_FOUND} に読み替える（どちらも行の排他と事前の判定のため API からは届かない最後の守り）。
     */
    private RoleChangeResult changeResult(
            RoleFirstStep<Void> step, RoleOperation operation, long actorUserId, long roleId, RequestOrigin origin) {
        return switch (step) {
            case RoleFirstStep.Done<Void> _ -> new RoleChangeResult.Done();
            case RoleFirstStep.Rejected<Void>(RoleRejection rejection, RoleAuditDetail detail) -> {
                publishFailure(operation, actorUserId, roleId, rejection, detail, origin);
                yield rejection == RoleRejection.IN_USE
                        ? inUse((RoleAuditDetail.InUse) detail)
                        : new RoleChangeResult.Rejected(rejection);
            }
            case RoleFirstStep.Store<Void>(RoleStoreOutcome<?> outcome, RoleAuditDetail detail) ->
                switch (outcome) {
                    case RoleStoreOutcome.Busy<?> _ -> new RoleChangeResult.Busy();
                    case RoleStoreOutcome.NameTaken<?> _
                    when operation == RoleOperation.RENAME -> {
                        publishFailure(operation, actorUserId, roleId, RoleRejection.NAME_DUPLICATE, detail, origin);
                        yield new RoleChangeResult.Rejected(RoleRejection.NAME_DUPLICATE);
                    }
                    case RoleStoreOutcome.Referenced<?>(Referent who)
                    when who == Referent.ROLE && operation == RoleOperation.DELETE ->
                        inUseAfterViolation(actorUserId, roleId, ((RoleAuditDetail.Name) detail).name(), origin);
                    case RoleStoreOutcome.Referenced<?>(Referent who)
                    when who == Referent.ROLE && operation == RoleOperation.CHANGE_PERMISSIONS -> {
                        publishFailure(operation, actorUserId, roleId, RoleRejection.ROLE_NOT_FOUND, null, origin);
                        yield new RoleChangeResult.Rejected(RoleRejection.ROLE_NOT_FOUND);
                    }
                    default -> throw unexpected(operation, outcome);
                };
        };
    }

    /** 削除の違反の後に、2つ目のトランザクションで残りの数を数え直し、失敗の出来事を出す。 */
    private RoleChangeResult inUseAfterViolation(long actorUserId, long roleId, String name, RequestOrigin origin) {
        RoleAuditDetail.InUse detail = Objects.requireNonNull(failureTransaction.execute(status -> {
            RoleAuditDetail.InUse inUse = new RoleAuditDetail.InUse(
                    name,
                    Math.toIntExact(assignments.countUsersOfRole(roleId)),
                    Math.toIntExact(assignments.countGroupsOfRole(roleId)));
            eventPublisher.publishEvent(RoleAuditEvent.failed(
                    RoleOperation.DELETE,
                    actorUserId,
                    roleId,
                    RoleRejection.IN_USE.auditFailure(),
                    inUse,
                    clock.instant(),
                    origin));
            return inUse;
        }));
        return inUse(detail);
    }

    private static RoleChangeResult.InUse inUse(RoleAuditDetail.InUse detail) {
        return new RoleChangeResult.InUse(detail.assignedUsers(), detail.assignedGroups());
    }

    /** 1つ目を巻き戻した後に、2つ目のトランザクション（書き込みなし）で失敗の出来事だけを出す（BR8.5・BR11.2）。 */
    private void publishFailure(
            RoleOperation operation,
            long actorUserId,
            Long roleId,
            RoleRejection rejection,
            RoleAuditDetail detail,
            RequestOrigin origin) {
        RoleAuditEvent event = RoleAuditEvent.failed(
                operation, actorUserId, roleId, rejection.auditFailure(), detail, clock.instant(), origin);
        failureTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private RoleListResult.Page readPage(int page) {
        long total = roles.countAll();
        long offset = Paging.offsetOf(page);
        if (offset >= total) {
            return new RoleListResult.Page(List.of(), page, Paging.PAGE_SIZE, total);
        }
        List<RoleRowView> views = roles.findPage(PageRequest.of(page - 1, Paging.PAGE_SIZE));
        Set<Long> ids = views.stream().map(RoleRowView::roleId).collect(Collectors.toSet());
        Map<Long, Long> userCounts = assignments.countUsersByRoles(ids).stream()
                .collect(Collectors.toMap(AssignmentCount::id, AssignmentCount::count));
        Map<Long, Long> groupCounts = assignments.countGroupsByRoles(ids).stream()
                .collect(Collectors.toMap(AssignmentCount::id, AssignmentCount::count));
        List<RoleListResult.Row> rows = views.stream()
                .map(view -> new RoleListResult.Row(
                        view.roleId(),
                        view.name(),
                        userCounts.getOrDefault(view.roleId(), 0L),
                        groupCounts.getOrDefault(view.roleId(), 0L)))
                .toList();
        return new RoleListResult.Page(rows, page, Paging.PAGE_SIZE, total);
    }

    /** 名前の入力の誤りの理由を返す（{@link RoleName#parse(String)} が誤りを返したときだけ呼ぶ）。 */
    private static RoleNameValidation.Reason invalidReason(String rawName) {
        return ((RoleNameValidation.Invalid) RoleName.parse(rawName)).reason();
    }

    private static IllegalStateException unexpected(RoleOperation operation, RoleStoreOutcome<?> outcome) {
        return new IllegalStateException(operation + " の結果として想定外です: " + outcome);
    }
}
