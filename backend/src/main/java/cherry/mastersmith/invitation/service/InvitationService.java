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
package cherry.mastersmith.invitation.service;

import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationAvailability;
import cherry.mastersmith.invitation.domain.InvitationCancelledEvent;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationIssuedEvent;
import cherry.mastersmith.invitation.domain.InvitationPaging;
import cherry.mastersmith.invitation.domain.InvitationRequestValidation;
import cherry.mastersmith.invitation.domain.InvitationResentEvent;
import cherry.mastersmith.invitation.domain.InvitationState;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.domain.InvitationValidity;
import cherry.mastersmith.invitation.domain.SendResult;
import cherry.mastersmith.invitation.repository.InvitationRepository;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 招待の管理の業務処理（招待・一覧・送り直し・取り消し。契約 C5、BR1.5・BR2.1〜BR2.7・BR4.1〜BR4.5・BR5.1〜BR5.4・BR6.1〜BR6.4・
 * BR8.1、{@code performance-design.md} 2節、{@code reliability-design.md} 1節・2節）。
 *
 * <p>このクラスには {@code @Transactional} を付けず、短いトランザクションを {@link TransactionTemplate} で作る。招待・送り直しは
 * 「確定の短いトランザクション → トランザクションの外で送信 → 結果を記録する別の短いトランザクション」の順で、送信の間は内部DB の接続を
 * 持たない（ADR-009）。監査の出来事は確定のトランザクションの中で知らせ、確定の後に記録される。
 *
 * <p>同時の招待で同じメールアドレスの招待中の一意の制約に当たったときは、トランザクションの外で受けて勝った側の招待を引き、
 * {@link InviteResult.AlreadyPending} を返す。勝った側が見つからなければ1回だけやり直す（{@code reliability-design.md} 2.2）。
 */
@Service
public class InvitationService {

    /** 同じメールアドレスの招待中の一意の制約の名前（V8。大文字にそろえて比べる）。 */
    static final String PENDING_EMAIL_CONSTRAINT = "UK_INVITATIONS_PENDING_EMAIL";

    private final InvitationRepository repository;

    private final UserAccountService userAccountService;

    private final InvitationMailDispatcher dispatcher;

    private final InvitationTokenIssuer tokenIssuer;

    private final InvitationSettings settings;

    private final InvitationBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate transaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param repository 招待の DB アクセス
     * @param userAccountService 利用者の確かめ（契約 C2）
     * @param dispatcher 招待メールの送信の入口
     * @param tokenIssuer トークンの発行
     * @param settings 招待の設定
     * @param barrier 同時の操作の待ち合わせの口
     * @param eventPublisher 出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public InvitationService(
            InvitationRepository repository,
            UserAccountService userAccountService,
            InvitationMailDispatcher dispatcher,
            InvitationTokenIssuer tokenIssuer,
            InvitationSettings settings,
            InvitationBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.userAccountService = userAccountService;
        this.dispatcher = dispatcher;
        this.tokenIssuer = tokenIssuer;
        this.settings = settings;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** 確定のトランザクションの結果（内部だけ）。 */
    private sealed interface Issue {

        /** 確定した（トークンはこの後の送信にだけ使う）。 */
        record Issued(Invitation invitation, InvitationToken token) implements Issue {}

        /** 登録済み。 */
        record Registered() implements Issue {}

        /** 期限内の招待中がある。 */
        record Pending(long invitationId, int page) implements Issue {}
    }

    /**
     * 招待する（BR1.1〜BR1.5・BR2.1〜BR2.6・BR4.1〜BR4.5・BR8.1）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param command 入力
     * @return 結果
     */
    public InviteResult invite(long actorUserId, RequestOrigin origin, InviteCommand command) {
        List<FieldError> errors = InvitationRequestValidation.validate(command.email(), command.language());
        if (!errors.isEmpty()) {
            return new InviteResult.Invalid(errors);
        }
        InvitationAvailability availability = settings.availability();
        if (!availability.enabled()) {
            return new InviteResult.NotConfigured(availability.unavailableReasons());
        }
        InvitationEmail email = InvitationRequestValidation.toEmail(command.email());
        Language language = InvitationRequestValidation.toLanguage(command.language());
        Issue issue = issueWithOneRetry(actorUserId, origin, email, language);
        return switch (issue) {
            case Issue.Registered _ -> new InviteResult.EmailRegistered();
            case Issue.Pending pending -> new InviteResult.AlreadyPending(pending.invitationId(), pending.page());
            case Issue.Issued issued ->
                new InviteResult.Invited(sendAndRecord(issued, InvitationOperation.INVITE, actorUserId));
        };
    }

    /** 確定のトランザクションを行い、同時の招待の一意の違反では勝った側を引く（見つからなければ1回だけやり直す）。 */
    private Issue issueWithOneRetry(long actorUserId, RequestOrigin origin, InvitationEmail email, Language language) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> issueInTransaction(actorUserId, origin, email, language));
            } catch (DataIntegrityViolationException e) {
                if (!isPendingEmailViolation(e)) {
                    throw e;
                }
                Optional<Issue.Pending> winner = readOnly.execute(status -> repository
                        .findByEmailAndState(email, InvitationState.PENDING)
                        .map(this::pendingOf));
                if (winner != null && winner.isPresent()) {
                    return winner.get();
                }
                if (attempt >= 2) {
                    throw new IllegalStateException("同じメールアドレスの招待中の一意の制約に2回当たりました", e);
                }
            }
        }
    }

    private Issue issueInTransaction(long actorUserId, RequestOrigin origin, InvitationEmail email, Language language) {
        if (userAccountService.existsByEmail(new RedactedText(email.value()))) {
            return new Issue.Registered();
        }
        Instant now = clock.instant();
        Optional<Invitation> existing = repository.findPendingByEmailForUpdate(email);
        if (existing.isPresent()) {
            Invitation pending = existing.get();
            if (pending.isValidAt(now)) {
                return pendingOf(pending);
            }
            pending.replace(now);
            // 追記より先に置き換えを DB に届ける（招待中の一意の制約に自分で当たらないため）。
            repository.saveAndFlush(pending);
        }
        barrier.beforeInsert();
        InvitationToken token = tokenIssuer.issue();
        Invitation invitation = repository.saveAndFlush(
                new Invitation(email, language, token.hash(), actorUserId, now, now.plus(settings.validity())));
        eventPublisher.publishEvent(InvitationIssuedEvent.of(invitation.getInvitationId(), actorUserId, now, origin));
        return new Issue.Issued(invitation, token);
    }

    /** 招待中の招待の ID と、一覧でのページを返す（BR2.3）。 */
    private Issue.Pending pendingOf(Invitation invitation) {
        long before = repository.countBefore(
                InvitationState.PENDING, invitation.getInvitedAt(), invitation.getInvitationId());
        return new Issue.Pending(invitation.getInvitationId(), InvitationPaging.pageOf(before + 1));
    }

    /** 同じメールアドレスの招待中の一意の制約に当たった誤りかを返す。 */
    static boolean isPendingEmailViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.toUpperCase(Locale.ROOT).contains(PENDING_EMAIL_CONSTRAINT);
            }
        }
        return false;
    }

    /** トランザクションの外で送り、結果を別の短いトランザクションで記録して、要約を返す（BR4.1〜BR4.5）。 */
    private InvitationSummary sendAndRecord(Issue.Issued issued, InvitationOperation operation, long displayUserId) {
        Invitation invitation = issued.invitation();
        long invitationId = invitation.getInvitationId();
        MailSendResult result = dispatcher.dispatch(
                invitationId,
                operation,
                invitation.getEmail(),
                invitation.getLanguage(),
                settings.registrationUrl(issued.token()));
        SendResult sendResult = SendResult.of(result.isSent());
        byte[] tokenHash = issued.token().hash();
        transaction.executeWithoutResult(status -> repository.recordSendResult(invitationId, tokenHash, sendResult));
        return summaryOf(invitation, sendResult, clock.instant(), Map.of(displayUserId, displayName(displayUserId)));
    }

    /**
     * 招待中の招待の一覧を読む（BR5.1〜BR5.4）。
     *
     * @param rawPage 問い合わせの page（無ければ null）
     * @return 結果
     */
    public ListResult list(String rawPage) {
        OptionalInt parsed = InvitationPaging.parsePage(rawPage);
        if (parsed.isEmpty()) {
            return new ListResult.InvalidPage();
        }
        int page = parsed.getAsInt();
        InvitationPage result = readOnly.execute(status -> readPage(page));
        return new ListResult.Listed(result);
    }

    private InvitationPage readPage(int page) {
        long total = repository.countByState(InvitationState.PENDING);
        List<Invitation> rows = InvitationPaging.offsetOf(page) >= total
                ? List.of()
                : repository.findPage(InvitationState.PENDING, PageRequest.of(page - 1, InvitationPaging.PAGE_SIZE));
        Map<Long, String> names = new HashMap<>();
        for (Invitation row : rows) {
            names.computeIfAbsent(row.getInvitedByUserId(), this::displayName);
        }
        Instant now = clock.instant();
        List<InvitationSummary> items = rows.stream()
                .map(row -> summaryOf(row, row.getSendResult(), now, names))
                .toList();
        InvitationAvailability availability = settings.availability();
        return new InvitationPage(
                items,
                page,
                InvitationPaging.PAGE_SIZE,
                total,
                availability.enabled(),
                availability.unavailableReasons());
    }

    /** 招待した管理者の氏名だけを返す。利用者の行が無ければ空の文字列（メールアドレスへ切り替えない。BR5.3）。 */
    private String displayName(long userId) {
        return userAccountService
                .findDisplayName(userId)
                .map(RedactedText::value)
                .orElse("");
    }

    private static InvitationSummary summaryOf(
            Invitation invitation, SendResult sendResult, Instant now, Map<Long, String> names) {
        return new InvitationSummary(
                invitation.getInvitationId(),
                invitation.getEmail().value(),
                invitation.getLanguage().value(),
                names.getOrDefault(invitation.getInvitedByUserId(), ""),
                invitation.getInvitedAt(),
                invitation.getExpiresAt(),
                sendResult.apiValue(),
                InvitationValidity.isExpired(invitation.getState(), invitation.getExpiresAt(), now));
    }

    /**
     * 招待を送り直す（BR1.5・BR6.1・BR6.3・BR4.1〜BR4.5・BR8.1）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param invitationId 招待の ID
     * @return 結果
     */
    public ResendResult resend(long actorUserId, RequestOrigin origin, long invitationId) {
        InvitationAvailability availability = settings.availability();
        if (!availability.enabled()) {
            return new ResendResult.NotConfigured(availability.unavailableReasons());
        }
        Optional<Issue.Issued> issued =
                transaction.execute(status -> resendInTransaction(actorUserId, origin, invitationId));
        if (issued == null || issued.isEmpty()) {
            return new ResendResult.NotFound();
        }
        Invitation invitation = issued.get().invitation();
        return new ResendResult.Resent(
                sendAndRecord(issued.get(), InvitationOperation.RESEND, invitation.getInvitedByUserId()));
    }

    private Optional<Issue.Issued> resendInTransaction(long actorUserId, RequestOrigin origin, long invitationId) {
        Optional<Invitation> found = repository.findByIdForUpdate(invitationId);
        if (found.isEmpty() || found.get().getState() != InvitationState.PENDING) {
            return Optional.empty();
        }
        Invitation invitation = found.get();
        barrier.afterLock(invitationId);
        Instant now = clock.instant();
        InvitationToken token = tokenIssuer.issue();
        invitation.resend(token.hash(), now, now.plus(settings.validity()));
        repository.saveAndFlush(invitation);
        eventPublisher.publishEvent(InvitationResentEvent.of(invitationId, actorUserId, now, origin));
        return Optional.of(new Issue.Issued(invitation, token));
    }

    /**
     * 招待を取り消す（設定を確かめない。BR6.2・BR6.3・BR8.1）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param invitationId 招待の ID
     * @return 結果
     */
    public CancelResult cancel(long actorUserId, RequestOrigin origin, long invitationId) {
        Boolean cancelled = transaction.execute(status -> {
            Optional<Invitation> found = repository.findByIdForUpdate(invitationId);
            if (found.isEmpty() || found.get().getState() != InvitationState.PENDING) {
                return false;
            }
            barrier.afterLock(invitationId);
            Instant now = clock.instant();
            found.get().cancel(now);
            repository.saveAndFlush(found.get());
            eventPublisher.publishEvent(InvitationCancelledEvent.of(invitationId, actorUserId, now, origin));
            return true;
        });
        return Boolean.TRUE.equals(cancelled) ? new CancelResult.Cancelled() : new CancelResult.NotFound();
    }
}
