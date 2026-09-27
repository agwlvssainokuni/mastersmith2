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
package cherry.mastersmith.user.service;

import cherry.mastersmith.user.domain.DisplayName;
import cherry.mastersmith.user.domain.EmailAddress;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.PasswordPolicy;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * UserAccount の公開の操作（照合、利用者の読み取りと作成。契約 C2）。パスワードのハッシュは外へ出さない（ADR-001）。
 */
@Service
public class UserAccountService {

    /** メールアドレスの一意の制約の名前（V2。大文字にそろえて比べる）。 */
    static final String EMAIL_UNIQUE_CONSTRAINT = "UK_USERS_EMAIL";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final DummyPasswordHash dummyPasswordHash;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    /**
     * 業務処理を作る。
     *
     * @param userRepository 利用者の DB アクセス
     * @param passwordEncoder パスワードのハッシュの仕組み
     * @param dummyPasswordHash ダミーのハッシュ
     * @param eventPublisher 出来事の知らせ
     * @param clock 時計
     */
    public UserAccountService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DummyPasswordHash dummyPasswordHash,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = dummyPasswordHash;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * メールアドレスとパスワードを照合する。利用者の検索1回と照合1回を必ず行う（BR2.5、BR2.7）。
     *
     * <p>利用者がいない、またはパスワードが UTF-8 で 72 バイトを超えるときは、ダミーのハッシュで照合して不一致とする（BR2.8。
     * 72 バイトを超える入力は照合の仕組みに渡さない）。トランザクションと排他の外で呼ぶ。
     *
     * @param email 入力されたメールアドレス
     * @param password 入力されたパスワード
     * @return 照合の結果（ハッシュを含まない）
     */
    public PasswordVerification verifyPassword(String email, Password password) {
        String normalized = EmailAddress.normalize(email);
        Optional<User> user = userRepository.findByEmail(normalized);
        boolean fits = PasswordPolicy.fitsMaxBytes(password.value());
        boolean matched;
        if (user.isPresent() && fits) {
            matched = passwordEncoder.matches(password.value(), user.get().getPasswordHash());
        } else {
            passwordEncoder.matches(
                    fits ? password.value() : dummyPasswordHash.substituteInput(), dummyPasswordHash.hash());
            matched = false;
        }
        return new PasswordVerification(
                normalized, user.map(UserAccountService::toSummary).orElse(null), matched);
    }

    /**
     * 利用者を ID で読む（要求ごとの読み取り。BR4.5）。
     *
     * @param userId 利用者ID
     * @return 利用者の要約（いなければ空）
     */
    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(long userId) {
        return userRepository.findById(userId).map(UserAccountService::toSummary);
    }

    /**
     * メールアドレスの利用者がいるかを返す。
     *
     * @param email メールアドレス（そろえる前の値でよい）
     * @return いれば true
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(EmailAddress.normalize(email)).isPresent();
    }

    /**
     * 氏名を ID で読む（契約 C2。招待の一覧の「招待した管理者」の表示に使う）。
     *
     * @param userId 利用者ID
     * @return 氏名（いなければ空）
     */
    @Transactional(readOnly = true)
    public Optional<String> findDisplayName(long userId) {
        return userRepository.findById(userId).map(User::getDisplayName);
    }

    /**
     * 言語を ID で読む（契約 C2。招待の言語の初期値に使う）。
     *
     * @param userId 利用者ID
     * @return 言語（いなければ空）
     */
    @Transactional(readOnly = true)
    public Optional<Language> findLanguage(long userId) {
        return userRepository.findById(userId).map(User::getLanguage);
    }

    /**
     * 利用者を作る（契約 C2、BR5.1〜BR5.3）。パスワードはハッシュにして保存し、同じトランザクションで {@link UserCreatedEvent} を知らせる。
     *
     * <ul>
     *   <li>受け取った値は決まりに合うことを前提とし、合わなければ作らずに想定外の誤り（{@link IllegalArgumentException}）とする。
     *       呼び出し元の入力の検証の代わりにしない（BR5.1）
     *   <li>メールアドレスを小文字にそろえ、パスワードのハッシュを計算する前に、その利用者がいるかを1回読んで確かめる。いれば作らずに
     *       {@link CreateUserResult.EmailAlreadyUsed} を返す。同時の作成で一意の制約に当たったときも同じ結果を返す（BR5.2）
     *   <li>呼び出し元のトランザクションが有ればそれに参加し、無ければ新しく始める（BR5.3）
     * </ul>
     *
     * <p><strong>呼び出し元の前提</strong>: {@link CreateUserResult.EmailAlreadyUsed} を返すとき、今のトランザクションに巻き戻しの印を
     * 付ける（一意の制約に当たったトランザクションは確定できないため。{@code reliability-design.md} 4節）。この操作が自分で始めた
     * トランザクションなら例外なしで巻き戻る。呼び出し元のトランザクションに参加しているときは、呼び出し元は必ず巻き戻す（確定させようと
     * すると {@code UnexpectedRollbackException} になる）。
     *
     * @param newUser 作成の入力
     * @return 作成の結果
     * @throws IllegalArgumentException 決まりに合わない値が来たとき（値は例外のメッセージに載せない）
     */
    @Transactional
    public CreateUserResult createUser(NewUser newUser) {
        Preferences preferences = requireAcceptable(newUser);
        String email = EmailAddress.normalize(newUser.email());
        if (userRepository.findByEmail(email).isPresent()) {
            return emailAlreadyUsed();
        }
        User user = new User(
                email,
                passwordEncoder.encode(newUser.password().value()),
                newUser.admin(),
                clock.instant(),
                preferences);
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (!isEmailUniqueViolation(e)) {
                throw e;
            }
            return emailAlreadyUsed();
        }
        eventPublisher.publishEvent(new UserCreatedEvent(saved.getUserId()));
        return new CreateUserResult.Created(saved.getUserId());
    }

    /** 作成の入力が決まりに合うことを確かめ、氏名と表示の設定の組を返す（BR5.1）。 */
    private static Preferences requireAcceptable(NewUser newUser) {
        Objects.requireNonNull(newUser, "newUser");
        if (!EmailAddress.isValid(EmailAddress.normalize(newUser.email()))) {
            throw new IllegalArgumentException("メールアドレスが決まりに合いません");
        }
        if (newUser.password() == null
                || !PasswordPolicy.isAcceptableForCreation(newUser.password().value())) {
            throw new IllegalArgumentException("パスワードが作成時の規則に合いません");
        }
        if (newUser.language() == null || newUser.theme() == null || newUser.fontSize() == null) {
            throw new IllegalArgumentException("表示の設定の値がありません");
        }
        return new Preferences(
                DisplayName.requireValid(newUser.displayName()),
                newUser.language(),
                newUser.theme(),
                newUser.fontSize());
    }

    /** 今のトランザクションに巻き戻しの印を付けて、登録済みの結果を返す。 */
    private static CreateUserResult emailAlreadyUsed() {
        // トランザクションの外で直接呼ばれたとき（業務処理の単体テスト）は、巻き戻すものが無い。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new CreateUserResult.EmailAlreadyUsed();
    }

    /** 一意の制約 uk_users_email に当たった誤りかを返す（ほかの制約の誤りは想定外として呼び出し元へ伝える）。 */
    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.toUpperCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT);
            }
        }
        return false;
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(
                user.getUserId(),
                user.getEmail(),
                user.isAdminFlag(),
                user.getDisplayName(),
                user.getLanguage().value(),
                user.getTheme().value(),
                user.getFontSize().value());
    }
}
