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
package cherry.mastersmith.useradmin.service;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.service.LockAdministrationService;
import cherry.mastersmith.auth.service.LoginFailureResetPreparation;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.common.paging.Paging;
import cherry.mastersmith.common.persistence.RowLockFailures;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.service.AdminRowsLock;
import cherry.mastersmith.user.service.ProfileCommand;
import cherry.mastersmith.user.service.ProfileUpdateResult;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSlice;
import cherry.mastersmith.user.service.UserAdminSummary;
import cherry.mastersmith.user.service.UserRowLock;
import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.domain.OperationFacts;
import cherry.mastersmith.useradmin.domain.RejectionPolicy;
import cherry.mastersmith.useradmin.domain.RejectionReason;
import cherry.mastersmith.useradmin.domain.UserAdminAuditEvent;
import cherry.mastersmith.useradmin.domain.UserAdminAuditFailure;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の管理の業務処理（Intent 260930-user-admin の U3、契約 C3）。一覧と氏名・言語の変更（B3）と、状態を変える5つの操作（B4）を持つ。
 *
 * <p>このクラスには {@code @Transactional} を付けず、トランザクションを {@link TransactionTemplate} で作る。一覧は読み取りだけの
 * 1つのトランザクションで、全体の件数・行・ロックの判定を読む（BR1.6）。一覧と氏名・言語の変更は監査の出来事を出さない（BR1.9・BR5.3）。
 *
 * <p>5つの操作は、それぞれ1つのトランザクションで、行の排他 → 待ち合わせの口 → 拒否の判定 → 操作した人の確かめ直し → 書き換え →
 * 監査の出来事の順に進む（FS の 2.2〜2.7、BR4.6）。排他の前に書き込みは無い。排他を取れなかったとき（Busy）は、口の形にかかわらず
 * 必ず先に {@link TransactionStatus#setRollbackOnly()} を付け、その後に DB に触れずに {@link OperationResult.Busy} を返す（BR3.5、
 * {@code reliability-design.md} 5.1 の決まり 3）。拒否は書き込みなしで確定させ、失敗の監査の出来事を出す（BR2.4・BR6.3）。値は投影
 * （{@link UserAdminSummary}）と ID だけで持ち、JPA のエンティティを持たない。C1 の口（停止の書き換え）の後に、先に読んだ値で書くことは
 * しない（U1 のレビュー R-03）。業務のログは出さない（{@code observability-design.md} 3節）。ただし、止める操作のリフレッシュトークンの
 * 書き込みの待ちの上限切れだけは、ここで受けて排他の種類の WARN を出す（コード生成のレビューの R-01）。
 */
@Service
public class UserAdminService {

    /** 検索の文字の項目の名前（契約 C3）。 */
    static final String Q = "q";

    /** リフレッシュトークンの行の書き込みの待ちの上限切れの WARN に載せる排他の種類（レビューの R-01）。 */
    static final String REFRESH_TOKEN_ROWS = "REFRESH_TOKEN_ROWS";

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdminService.class);

    private final UserAccountService userAccounts;

    private final LockAdministrationService locks;

    private final RefreshTokenRevocationService revocations;

    private final UserAdminBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate transaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param userAccounts UserAccount の口（C8・C1）
     * @param locks Authentication の利用者の管理に向けた口（C8）
     * @param revocations リフレッシュトークンのまとめての無効化（C1）
     * @param barrier 有効な管理者を数える直前の待ち合わせの口
     * @param eventPublisher 監査の出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public UserAdminService(
            UserAccountService userAccounts,
            LockAdministrationService locks,
            RefreshTokenRevocationService revocations,
            UserAdminBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.userAccounts = userAccounts;
        this.locks = locks;
        this.revocations = revocations;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * 利用者の一覧の1ページを読む（BR1.1〜BR1.9）。判定の順は page → q（どちらも誤りなら page の誤り。BR1.4）。
     *
     * @param actorId 操作している管理者の利用者 ID（self の判定に使う）
     * @param rawPage 要求の page（無ければ null）
     * @param search 検索の文字（無ければ null）
     * @return 結果
     */
    public UserAdminListResult list(long actorId, String rawPage, SearchText search) {
        OptionalInt parsed = Paging.parsePage(rawPage);
        if (parsed.isEmpty()) {
            return new UserAdminListResult.InvalidPage();
        }
        if (search != null && search.isTooLong()) {
            return new UserAdminListResult.Invalid(List.of(new FieldError(Q, FieldErrorReason.TOO_LONG)));
        }
        int page = parsed.getAsInt();
        UserAdminPage result = readOnly.execute(status -> readPage(actorId, page, search));
        return new UserAdminListResult.Listed(Objects.requireNonNull(result, "result"));
    }

    /**
     * 管理者による氏名と言語の変更（BR5.1〜BR5.4）。1つのトランザクションで UserAccount の口を呼ぶ。行の排他・操作した人の
     * 確かめ直し・監査はしない。
     *
     * @param userId 対象の利用者 ID
     * @param command 氏名と言語（検証の前の値）
     * @return 結果
     */
    public ProfileUpdateResult updateProfile(long userId, ProfileCommand command) {
        Objects.requireNonNull(command, "command");
        return Objects.requireNonNull(
                transaction.execute(status -> userAccounts.updateProfile(userId, command)), "result");
    }

    /**
     * 管理者の印を付ける（POST /api/admin/users/{userId}/grant-admin、FS の 2.3、BR4.1）。トークンには触れない。
     *
     * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
     * @param origin 要求の送り手の情報
     * @param targetUserId 対象の利用者 ID（要求の値のまま）
     * @return 結果
     */
    public OperationResult grantAdmin(long actorUserId, RequestOrigin origin, long targetUserId) {
        return adminRowsOperation(AdminOperation.GRANT_ADMIN, actorUserId, origin, targetUserId);
    }

    /**
     * 管理者の印を外す（revoke-admin、FS の 2.4、BR4.2・BR3.2）。トークンには触れない。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param targetUserId 対象の利用者 ID
     * @return 結果
     */
    public OperationResult revokeAdmin(long actorUserId, RequestOrigin origin, long targetUserId) {
        return adminRowsOperation(AdminOperation.REVOKE_ADMIN, actorUserId, origin, targetUserId);
    }

    /**
     * 利用を止める（suspend、FS の 2.5、BR4.3・BR3.2）。同じトランザクションで停止の状態を書き換え、リフレッシュトークンをすべて無効に
     * する。失敗回数とロックの状態は変えない。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param targetUserId 対象の利用者 ID
     * @return 結果
     */
    public OperationResult suspend(long actorUserId, RequestOrigin origin, long targetUserId) {
        return adminRowsOperation(AdminOperation.SUSPEND, actorUserId, origin, targetUserId);
    }

    /**
     * 停止を解く（resume、FS の 2.6、BR4.4・BR3.3）。対象の行だけを排他し、トークンは戻さない。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param targetUserId 対象の利用者 ID
     * @return 結果
     */
    public OperationResult resume(long actorUserId, RequestOrigin origin, long targetUserId) {
        Objects.requireNonNull(origin, "origin");
        AdminOperation operation = AdminOperation.RESUME;
        return execute(status -> {
            UserRowLock lock = userAccounts.lockUserRow(targetUserId);
            if (!(lock instanceof UserRowLock.Locked locked)) {
                return busy(status);
            }
            OperationFacts facts = locked.target()
                    .map(target -> factsOf(target, actorUserId, false, false))
                    .orElseGet(OperationFacts::targetMissing);
            Optional<RejectionReason> reason = RejectionPolicy.decide(operation, facts);
            if (reason.isPresent()) {
                return rejected(operation, actorUserId, origin, targetUserId, reason.get());
            }
            if (!isActiveAdmin(actorUserId)) {
                return operatorNotAdmin(operation, actorUserId, origin, targetUserId);
            }
            userAccounts.setSuspended(targetUserId, false);
            return done(operation, actorUserId, origin, targetUserId);
        });
    }

    /**
     * ログインの失敗回数を戻す（reset-login-failures、FS の 2.7、BR4.5・BR3.4）。利用者の行は排他せず、ロックの状態の行だけを排他する
     * （2段の口）。自分自身と停止中の利用者にも許す。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param targetUserId 対象の利用者 ID
     * @return 結果
     */
    public OperationResult resetLoginFailures(long actorUserId, RequestOrigin origin, long targetUserId) {
        Objects.requireNonNull(origin, "origin");
        AdminOperation operation = AdminOperation.RESET_LOGIN_FAILURES;
        return execute(status -> {
            // 対象の有無を先に排他なしで読む（負の ID もここで止まり、ダミーの行に触れない。BR2.7）。
            Optional<UserAdminSummary> target = userAccounts.findAdminSummary(targetUserId);
            if (target.isEmpty()) {
                return rejected(operation, actorUserId, origin, targetUserId, RejectionReason.USER_NOT_FOUND);
            }
            LoginFailureResetPreparation preparation = locks.prepareFailureReset(targetUserId);
            if (preparation instanceof LoginFailureResetPreparation.Busy) {
                return busy(status);
            }
            boolean resettable = preparation instanceof LoginFailureResetPreparation.Ready;
            OperationFacts facts = factsOf(target.get(), actorUserId, resettable, false);
            Optional<RejectionReason> reason = RejectionPolicy.decide(operation, facts);
            if (reason.isPresent()) {
                return rejected(operation, actorUserId, origin, targetUserId, reason.get());
            }
            if (!isActiveAdmin(actorUserId)) {
                return operatorNotAdmin(operation, actorUserId, origin, targetUserId);
            }
            locks.completeFailureReset(targetUserId);
            return done(operation, actorUserId, origin, targetUserId);
        });
    }

    /**
     * 印を付ける・外す・止めるの共通の流れ（FS の 2.3〜2.5）。管理者の行と対象の行を利用者 ID の昇順に排他し、排他の後の有効な管理者の
     * 集合で判定と確かめ直しを行う（BR3.1・BR2.5）。
     */
    private OperationResult adminRowsOperation(
            AdminOperation operation, long actorUserId, RequestOrigin origin, long targetUserId) {
        Objects.requireNonNull(origin, "origin");
        return execute(status -> {
            AdminRowsLock lock = userAccounts.lockAdminRowsInIdOrder(targetUserId);
            if (!(lock instanceof AdminRowsLock.Locked locked)) {
                return busy(status);
            }
            barrier.beforeCount(operation, targetUserId);
            Set<Long> activeAdmins = locked.activeAdminIds();
            boolean leavesNoActiveAdmin =
                    activeAdmins.contains(targetUserId) && activeAdmins.stream().allMatch(id -> id == targetUserId);
            OperationFacts facts = locked.target()
                    .map(target -> factsOf(target, actorUserId, false, leavesNoActiveAdmin))
                    .orElseGet(OperationFacts::targetMissing);
            Optional<RejectionReason> reason = RejectionPolicy.decide(operation, facts);
            if (reason.isPresent()) {
                return rejected(operation, actorUserId, origin, targetUserId, reason.get());
            }
            if (!activeAdmins.contains(actorUserId)) {
                return operatorNotAdmin(operation, actorUserId, origin, targetUserId);
            }
            switch (operation) {
                case GRANT_ADMIN -> userAccounts.setAdmin(targetUserId, true);
                case REVOKE_ADMIN -> userAccounts.setAdmin(targetUserId, false);
                case SUSPEND -> {
                    userAccounts.setSuspended(targetUserId, true);
                    if (!revokeAllRefreshTokens(targetUserId)) {
                        // 停止の書き換えの後でも、巻き戻しの印を付けて巻き戻すため、停止もトークンも残らない（BR3.5）。
                        return busy(status);
                    }
                }
                case RESUME, RESET_LOGIN_FAILURES ->
                    throw new IllegalArgumentException("管理者の行を排他する操作ではありません: " + operation);
            }
            return done(operation, actorUserId, origin, targetUserId);
        });
    }

    /**
     * 止める操作で、対象のリフレッシュトークンをすべて無効にする（C1）。リフレッシュトークンの行が別の要求（トークンの更新・ログイン
     * など）に持たれていて、書き込みの問い合わせの待ちが上限切れ（H2 の既定、約 2 秒）になったときは、例外を外へ出さずに偽を返し、
     * 呼び出し元が Busy にする（コード生成のレビューの R-01、BR3.5）。
     *
     * <p>WARN には排他の種類と例外のクラスの名前だけを出し、例外そのもの（行の値を含みうる連なりの文）は渡さない
     * （{@code security-design.md} 7節）。排他の失敗でない例外はそのまま外へ出す（想定外の誤り）。
     *
     * @param targetUserId 対象の利用者 ID
     * @return 無効にできたら真、待ちの上限切れなら偽
     */
    private boolean revokeAllRefreshTokens(long targetUserId) {
        try {
            revocations.revokeAllRefreshTokens(targetUserId);
            return true;
        } catch (RuntimeException e) {
            if (!RowLockFailures.isLockFailure(e)) {
                throw e;
            }
            RowLockFailures.warn(LOGGER, REFRESH_TOKEN_ROWS, e);
            return false;
        }
    }

    /** 1つの読み書きのトランザクションで操作を行う（Busy のときは中で巻き戻しの印を付けて、例外なしで巻き戻る）。 */
    private OperationResult execute(Function<TransactionStatus, OperationResult> work) {
        return Objects.requireNonNull(transaction.execute(work::apply), "result");
    }

    private static OperationFacts factsOf(
            UserAdminSummary target, long actorUserId, boolean resettable, boolean leavesNoActiveAdmin) {
        return new OperationFacts(
                true,
                target.userId() == actorUserId,
                target.suspended(),
                target.admin(),
                resettable,
                leavesNoActiveAdmin);
    }

    /** 操作した人を排他なしで読み直し、いて・印を持ち・停止していないかを返す（停止を解く・失敗回数を戻す。BR2.5）。 */
    private boolean isActiveAdmin(long actorUserId) {
        return userAccounts
                .findAdminSummary(actorUserId)
                .map(actor -> actor.admin() && !actor.suspended())
                .orElse(false);
    }

    /** 巻き戻しの印を先に付け、その後に DB に触れずに Busy を返す（監査を出さない。BR3.5・BR6.4）。 */
    private static OperationResult busy(TransactionStatus status) {
        status.setRollbackOnly();
        return new OperationResult.Busy();
    }

    private OperationResult rejected(
            AdminOperation operation,
            long actorUserId,
            RequestOrigin origin,
            long targetUserId,
            RejectionReason reason) {
        eventPublisher.publishEvent(UserAdminAuditEvent.failed(
                operation, actorUserId, targetUserId, UserAdminAuditFailure.of(reason), clock.instant(), origin));
        return new OperationResult.Rejected(reason);
    }

    private OperationResult operatorNotAdmin(
            AdminOperation operation, long actorUserId, RequestOrigin origin, long targetUserId) {
        eventPublisher.publishEvent(UserAdminAuditEvent.failed(
                operation, actorUserId, targetUserId, UserAdminAuditFailure.NOT_ADMIN, clock.instant(), origin));
        return new OperationResult.OperatorNotAdmin();
    }

    private OperationResult done(AdminOperation operation, long actorUserId, RequestOrigin origin, long targetUserId) {
        eventPublisher.publishEvent(
                UserAdminAuditEvent.succeeded(operation, actorUserId, targetUserId, clock.instant(), origin));
        return new OperationResult.Done();
    }

    private UserAdminPage readPage(long actorId, int page, SearchText search) {
        UserAdminSlice slice = userAccounts.findAdminPage(search, Paging.offsetOf(page), Paging.PAGE_SIZE);
        List<Long> ids = slice.items().stream().map(UserAdminSummary::userId).toList();
        Map<Long, LockView> views = locks.lockViewsOf(ids);
        List<UserAdminEntry> entries = slice.items().stream()
                .map(summary -> new UserAdminEntry(
                        summary, views.getOrDefault(summary.userId(), LockView.NONE), summary.userId() == actorId))
                .toList();
        return new UserAdminPage(entries, page, Paging.PAGE_SIZE, slice.total());
    }
}
