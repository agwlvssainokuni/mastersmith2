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
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.domain.InvitationValidity;
import cherry.mastersmith.invitation.domain.LinkRejection;
import cherry.mastersmith.invitation.domain.RegistrationCompletedEvent;
import cherry.mastersmith.invitation.domain.RegistrationFailedEvent;
import cherry.mastersmith.invitation.domain.RegistrationValidation;
import cherry.mastersmith.invitation.repository.InvitationRepository;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.service.CreateUserResult;
import cherry.mastersmith.user.service.NewUser;
import cherry.mastersmith.user.service.UserAccountService;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * リンクの確かめと登録の完了の業務処理（契約 C6、BR3.2・BR3.3・BR7.1〜BR7.7・BR8.2・BR8.3、{@code performance-design.md} 3節、
 * {@code reliability-design.md} 3節）。ログインなしの要求から呼ばれる。
 *
 * <ul>
 *   <li>リンクの確かめは内部DB を変えず、出来事を出さない
 *   <li>登録の完了は、入力の検証（DB を使わない）→ トークンの形の確かめ → 1つのトランザクション（行の排他・判定・利用者の作成・完了）
 *       の順。拒否が決まったら自分の巻き戻しの印を付けて抜け（{@code createUser} が付けた印だけで確定しようとすると
 *       {@code UnexpectedRollbackException} になるため）、トランザクションの外で REGISTRATION_FAILED を知らせる
 * </ul>
 */
@Service
public class RegistrationService {

    private final InvitationRepository repository;

    private final UserAccountService userAccountService;

    private final InvitationBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate transaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param repository 招待の DB アクセス
     * @param userAccountService 利用者の確かめと作成（契約 C2）
     * @param barrier 同時の操作の待ち合わせの口
     * @param eventPublisher 出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public RegistrationService(
            InvitationRepository repository,
            UserAccountService userAccountService,
            InvitationBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.userAccountService = userAccountService;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** 完了のトランザクションの結果（内部だけ）。 */
    private sealed interface Outcome {

        /** 完了した。 */
        record Done() implements Outcome {}

        /** 拒否した（招待の ID は見つかったときだけ）。 */
        record Refused(Long invitationId, LinkRejection reason) implements Outcome {}
    }

    /**
     * リンクを確かめる（BR7.1）。
     *
     * @param command 入力（トークン）
     * @return 結果
     */
    public VerifyResult verify(TokenCommand command) {
        Optional<InvitationToken> token = InvitationToken.parse(command.token());
        if (token.isEmpty()) {
            return new VerifyResult.Rejected();
        }
        byte[] hash = token.get().hash();
        Optional<InvitationView> view = readOnly.execute(status -> repository
                .findByTokenHash(hash)
                .filter(invitation -> invitation.isValidAt(clock.instant()))
                .filter(invitation -> !userAccountService.existsByEmail(
                        new RedactedText(invitation.getEmail().value())))
                .map(invitation -> new InvitationView(
                        invitation.getEmail().value(), invitation.getLanguage().value())));
        return view == null || view.isEmpty() ? new VerifyResult.Rejected() : new VerifyResult.Valid(view.get());
    }

    /**
     * 登録を完了する（BR7.2〜BR7.6・BR8.2・BR8.3）。
     *
     * @param origin 要求の送り手の情報
     * @param command 入力
     * @return 結果
     */
    public CompleteResult complete(RequestOrigin origin, RegistrationCommand command) {
        List<FieldError> errors = RegistrationValidation.validate(
                command.displayName(),
                command.password(),
                command.passwordConfirmation(),
                command.language(),
                command.theme(),
                command.fontSize());
        if (!errors.isEmpty()) {
            return new CompleteResult.Invalid(errors);
        }
        Optional<InvitationToken> token = InvitationToken.parse(command.token());
        Outcome outcome = token.isEmpty()
                ? new Outcome.Refused(null, LinkRejection.INVITATION_NOT_FOUND)
                : transaction.execute(status -> completeInTransaction(status, token.get(), command, origin));
        if (outcome instanceof Outcome.Refused refused) {
            eventPublisher.publishEvent(
                    RegistrationFailedEvent.of(refused.invitationId(), refused.reason(), clock.instant(), origin));
            return new CompleteResult.Rejected();
        }
        return new CompleteResult.Completed();
    }

    private Outcome completeInTransaction(
            TransactionStatus status, InvitationToken token, RegistrationCommand command, RequestOrigin origin) {
        byte[] hash = token.hash();
        Optional<Invitation> found = repository.findByTokenHashForUpdate(hash);
        // 排他を待つ間に送り直されたときは、排他を得た行のハッシュが違う（前のトークンは見つからない扱い。BR3.2・BR6.4）。
        if (found.isEmpty() || !MessageDigest.isEqual(found.get().getTokenHash(), hash)) {
            return refuse(status, null, LinkRejection.INVITATION_NOT_FOUND);
        }
        Invitation invitation = found.get();
        long invitationId = invitation.getInvitationId();
        barrier.afterLock(invitationId);
        Instant now = clock.instant();
        Optional<LinkRejection> rejection =
                InvitationValidity.rejectionOf(invitation.getState(), invitation.getExpiresAt(), now);
        if (rejection.isPresent()) {
            return refuse(status, invitationId, rejection.get());
        }
        CreateUserResult created = userAccountService.createUser(new NewUser(
                invitation.getEmail().value(),
                command.displayName(),
                new Password(command.password()),
                Language.fromValue(command.language()).orElseThrow(),
                Theme.fromValue(command.theme()).orElseThrow(),
                FontSize.fromValue(command.fontSize()).orElseThrow(),
                false));
        return switch (created) {
            case CreateUserResult.EmailAlreadyUsed _ ->
                refuse(status, invitationId, LinkRejection.EMAIL_ALREADY_REGISTERED);
            case CreateUserResult.Created user -> {
                invitation.complete(user.userId(), now);
                repository.saveAndFlush(invitation);
                eventPublisher.publishEvent(RegistrationCompletedEvent.of(invitationId, user.userId(), now, origin));
                yield new Outcome.Done();
            }
        };
    }

    /** 自分の巻き戻しの印を付けて拒否する（利用者も招待の変更も残さない）。 */
    private static Outcome refuse(TransactionStatus status, Long invitationId, LinkRejection reason) {
        status.setRollbackOnly();
        return new Outcome.Refused(invitationId, reason);
    }
}
