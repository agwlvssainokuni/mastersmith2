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

import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 自分の作業ロールの読み取りと切り替えの業務処理（Intent 261004-role-menu の U4、{@code logical-components.md} の L3、FS の 2.10、
 * BR7.1〜BR7.8・BR11.3・BR11.4）。
 *
 * <p>主体（利用者 ID）は要求の文脈から読んだ値だけを受け、他人を指す値は受け取らない（BR2.2）。有効な作業ロールは {@link UserRoles} の
 * 決め方（解決の口と同じ {@code WorkRoleChooser}）で決める。読み取りは保存を書き換えず、監査に残さない（BR7.3）。
 *
 * <p>切り替えはロールの行の排他を取らない（BR7.8）。1つ目のトランザクションを {@link RoleStoreTransactions} で組み、利用者のロールを読む →
 * 含まれなければ {@code ROLE_NOT_ASSIGNED}（存在しないロールも同じ。BR7.5）→ 待ち合わせの口 → 保存がすでに選んだロールを指していれば
 * 書かずに終わる（監査なし。BR7.6）→ それ以外は保存を書き（無ければ作り）、{@code WORK_ROLE_SWITCHED} を出す（BR7.6・BR7.7）。拒否は
 * 1つ目を巻き戻した後に2つ目のトランザクションで失敗の出来事を出す。同じ利用者の保存の主キーの待ちの上限切れと違反は {@code ROLE_BUSY}
 * （監査なし。BR8.4）。
 */
@Service
public class WorkRoleService {

    private final RoleStoreTransactions transactions;

    private final UserRoleReader userRoles;

    private final RoleBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate failureTransaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param transactions store を呼ぶ1つ目のトランザクションの入口
     * @param userRoles 利用者のロールを決める読み取り
     * @param barrier 待ち合わせの口
     * @param eventPublisher 監査の出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public WorkRoleService(
            RoleStoreTransactions transactions,
            UserRoleReader userRoles,
            RoleBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
        this.userRoles = userRoles;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * 自分のロールと今の作業ロールを読む（契約 C8 の GET、BR7.1・BR7.2）。{@code current} は解決の口の {@code effectiveWorkRole} と同じ
     * 決め方の結果（同じ読み取りから求めるため、問い合わせを重ねない）。
     *
     * @param userId 主体の利用者 ID（要求の文脈から読んだ値）
     * @return ロール（ID の順）と有効な作業ロール
     */
    public WorkRoleView read(long userId) {
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    UserRoles read = userRoles.read(userId);
                    return new WorkRoleView(read.roles(), read.effective());
                }),
                "result");
    }

    /**
     * 作業ロールを切り替える（契約 C8 の PUT、BR7.5〜BR7.8）。
     *
     * @param userId 主体の利用者 ID（要求の文脈から読んだ値。監査の操作した人と対象の利用者）
     * @param origin 要求の送り手の情報
     * @param roleId 選んだロールの ID（要求の値のまま）
     * @return 結果
     */
    public WorkRoleSwitchResult switchTo(long userId, RequestOrigin origin, long roleId) {
        Objects.requireNonNull(origin, "origin");
        RoleOperation operation = RoleOperation.SWITCH_WORK_ROLE;
        String key = String.valueOf(userId);
        RoleFirstStep<Boolean> step = transactions.inFirst(store -> {
            UserRoles read = userRoles.read(userId);
            Optional<WorkRoleRef> chosen = read.roles().stream()
                    .filter(role -> role.roleId() == roleId)
                    .findFirst();
            if (chosen.isEmpty()) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NOT_ASSIGNED, null);
            }
            barrier.afterCheck(operation, key);
            OptionalLong stored = read.stored();
            if (stored.isPresent() && stored.getAsLong() == roleId) {
                return new RoleFirstStep.Done<>(false);
            }
            Optional<WorkRoleRef> before = read.effective();
            RoleStoreOutcome<Void> outcome = store.saveWorkRoleSelection(userId, roleId, clock.instant());
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, null);
            }
            barrier.afterWrite(operation, key);
            RoleAuditDetail detail = new RoleAuditDetail.WorkRoleSwitch(
                    before.map(WorkRoleRef::roleId).orElse(null),
                    before.map(WorkRoleRef::name).orElse(null),
                    chosen.get().name(),
                    stored.isPresent() ? stored.getAsLong() : null);
            eventPublisher.publishEvent(
                    RoleAuditEvent.succeeded(operation, userId, roleId, userId, null, detail, clock.instant(), origin));
            return new RoleFirstStep.Done<>(true);
        });
        return switch (step) {
            case RoleFirstStep.Done<Boolean>(Boolean written) ->
                Boolean.TRUE.equals(written)
                        ? new WorkRoleSwitchResult.Switched()
                        : new WorkRoleSwitchResult.Unchanged();
            case RoleFirstStep.Rejected<Boolean> _ -> {
                RoleAuditEvent event = RoleAuditEvent.failed(
                        operation,
                        userId,
                        roleId,
                        userId,
                        null,
                        RoleAuditFailure.ROLE_NOT_ASSIGNED,
                        null,
                        clock.instant(),
                        origin);
                failureTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
                yield new WorkRoleSwitchResult.NotAssigned();
            }
            case RoleFirstStep.Store<Boolean>(RoleStoreOutcome<?> outcome, RoleAuditDetail _) ->
                switch (outcome) {
                    case RoleStoreOutcome.Busy<?> _ -> new WorkRoleSwitchResult.Busy();
                    default -> throw new IllegalStateException(operation + " の結果として想定外です: " + outcome);
                };
        };
    }
}
