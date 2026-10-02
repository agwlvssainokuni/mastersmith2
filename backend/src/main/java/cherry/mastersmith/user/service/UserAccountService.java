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
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.PasswordPolicy;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.ProfileUpdate;
import cherry.mastersmith.user.domain.ProfileValidation;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserAdminRow;
import cherry.mastersmith.user.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
     * <p>メールアドレスは文字列にすると伏せる型で受け渡す（メソッドの呼び出しの追跡の TRACE に出さないため。Intent 260930-user-admin の
     * B1 のレビュー R-01 を受けた依頼者の決定）。
     *
     * @param email 入力されたメールアドレス（そろえる前の値でよい）
     * @param password 入力されたパスワード
     * @return 照合の結果（ハッシュを含まない）
     */
    public PasswordVerification verifyPassword(RedactedText email, Password password) {
        Objects.requireNonNull(email, "email");
        String normalized = EmailAddress.normalize(email.value());
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
     * 利用停止中かを返す（契約 C1、Intent 260930-user-admin の U1）。読み取りだけを行う。
     *
     * @param userId 利用者ID
     * @return 利用停止中なら true
     * @throws IllegalStateException 利用者がいないとき（例外のメッセージには利用者 ID だけを載せる）
     */
    @Transactional(readOnly = true)
    public boolean isSuspended(long userId) {
        return userRepository
                .findById(userId)
                .map(User::isSuspended)
                .orElseThrow(() -> new IllegalStateException("利用者がいません: userId=" + userId));
    }

    /**
     * 停止の状態を書き換える（契約 C1、Intent 260930-user-admin の U1、BR1.4・BR1.5）。停止の列だけを書く。
     *
     * <p>呼び出し元のトランザクションの中でだけ呼べる（無ければ {@code IllegalTransactionStateException}）。拒否の判定（最後の
     * 管理者の保護など）・監査・リフレッシュトークンの無効化はしない（呼び出し元の受け持ち）。
     *
     * <p><strong>呼び出し元の約束</strong>: 書いた後に持続化の文脈を空にするため、呼ぶ前に同じトランザクションで読み込んだ
     * エンティティは切り離される。呼んだ後に状態を知りたいときは、読み直す（{@link #findById(long)}・{@link #isSuspended(long)}）。
     *
     * @param userId 利用者ID
     * @param suspended 停止するなら true、解くなら false
     * @throws IllegalStateException 利用者がいないとき（例外のメッセージには利用者 ID だけを載せる）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setSuspended(long userId, boolean suspended) {
        if (userRepository.updateSuspended(userId, suspended) == 0) {
            throw new IllegalStateException("利用者がいません: userId=" + userId);
        }
    }

    /**
     * 利用者の一覧の1ページを読む（Intent 260930-user-admin の U3、契約 C8 の findAdminPage、BR1.1・BR1.2・BR1.5・BR1.6）。
     *
     * <p>呼び出し元の読み取りだけのトランザクションに入り（無ければ新しく始める）、全体の件数と行を同じトランザクションで読む。検索の
     * 文字は伏せる型のまま受け、問い合わせには伏せる型のパターンを渡す（BR7.4）。読み始めの位置が全体の件数以上なら行を読まない。
     * 長さの検証は呼び出し元が先に行う（BR1.4）。
     *
     * @param search 検索の文字（null か、前後の空白を除いて空なら検索なし）
     * @param offset 読み始めの位置（0 以上、{@code limit} の倍数。共通のページ送りの読み始めの位置）
     * @param limit 1ページの件数（1 以上）
     * @return 1ページの要約と全体の件数
     * @throws IllegalArgumentException 位置か件数が決まりに合わないとき
     */
    @Transactional(readOnly = true)
    public UserAdminSlice findAdminPage(SearchText search, long offset, int limit) {
        if (limit < 1 || offset < 0 || offset % limit != 0) {
            throw new IllegalArgumentException("読み始めの位置と件数が決まりに合いません: offset=" + offset + ", limit=" + limit);
        }
        boolean searching = search != null && !search.isBlank();
        RedactedText pattern = searching ? search.likePattern() : null;
        long total = searching ? userRepository.countBySearch(pattern) : userRepository.count();
        if (offset >= total) {
            return new UserAdminSlice(List.of(), total);
        }
        PageRequest pageable = PageRequest.of(Math.toIntExact(offset / limit), limit);
        List<UserAdminRow> rows = searching
                ? userRepository.findAdminRowsBySearch(pattern, pageable)
                : userRepository.findAdminRows(pageable);
        return new UserAdminSlice(
                rows.stream().map(UserAccountService::toAdminSummary).toList(), total);
    }

    /**
     * 管理者による氏名と言語の変更（Intent 260930-user-admin の U3、契約 C8 の updateProfile、BR5.1・BR5.2）。
     *
     * <p>呼び出し元のトランザクションの中でだけ呼べる（無ければ {@code IllegalTransactionStateException}）。先に検証し、誤りなら内部DB に
     * 触れずに返す。氏名と言語の2列だけを書き換え、行の排他・監査はしない。書いた後に持続化の文脈を空にするため、呼ぶ前に読み込んだ
     * エンティティは切り離される。
     *
     * @param userId 対象の利用者 ID
     * @param command 氏名と言語（検証の前の値）
     * @return 結果
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProfileUpdateResult updateProfile(long userId, ProfileCommand command) {
        Objects.requireNonNull(command, "command");
        List<FieldError> errors = ProfileValidation.validate(command.displayName(), command.language());
        if (!errors.isEmpty()) {
            return new ProfileUpdateResult.Invalid(errors);
        }
        ProfileUpdate update = ProfileValidation.toProfileUpdate(command.displayName(), command.language());
        if (userRepository.updateProfile(userId, update) == 0) {
            return new ProfileUpdateResult.NotFound();
        }
        return new ProfileUpdateResult.Updated();
    }

    /**
     * メールアドレスの利用者がいるかを返す（契約 C2。招待・登録の完了・初期管理者の作成の経路が使う。U3 の計画の決定 3）。
     *
     * <p>メールアドレスは文字列にすると伏せる型で受け渡す（メソッドの呼び出しの追跡の TRACE に出さないため）。文字列で受ける口は
     * Intent 260930-user-admin の B1 で消した（レビュー R-01 を受けた依頼者の決定）。
     *
     * @param email メールアドレス（そろえる前の値でよい）
     * @return いれば true
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(RedactedText email) {
        Objects.requireNonNull(email, "email");
        return userRepository.existsByRedactedEmail(new RedactedText(EmailAddress.normalize(email.value())));
    }

    /**
     * 氏名を ID で読む（契約 C2。招待の一覧の「招待した管理者」の表示に使う）。
     *
     * <p>氏名（既存の利用者の初期値はメールアドレス）は文字列にすると伏せる型で返す（U3 の計画の決定 3。契約 C2 の
     * {@code Optional<String>} との差は U3 のコード生成の記録に書く）。
     *
     * @param userId 利用者ID
     * @return 氏名（いなければ空）
     */
    @Transactional(readOnly = true)
    public Optional<RedactedText> findDisplayName(long userId) {
        return userRepository.findById(userId).map(user -> new RedactedText(user.getDisplayName()));
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
        if (userRepository.existsByRedactedEmail(new RedactedText(email))) {
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
                user.getFontSize().value(),
                user.isSuspended());
    }

    private static UserAdminSummary toAdminSummary(UserAdminRow row) {
        return new UserAdminSummary(
                row.userId(),
                row.email(),
                row.displayName(),
                row.language(),
                row.admin(),
                row.suspended(),
                row.registeredAt());
    }
}
