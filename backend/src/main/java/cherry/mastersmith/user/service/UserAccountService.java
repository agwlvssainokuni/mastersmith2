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

import cherry.mastersmith.user.domain.EmailAddress;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.PasswordPolicy;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserAccount の公開の操作（照合、利用者の読み取りと作成）。パスワードのハッシュは外へ出さない（ADR-001）。
 */
@Service
public class UserAccountService {

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
     * 利用者を作る。パスワードはハッシュにして保存し、同じトランザクションで {@link UserCreatedEvent} を知らせる（C8）。
     *
     * @param email メールアドレス（そろえる前の値でよい）
     * @param password パスワード
     * @param admin 管理者か
     * @return 作った利用者の要約
     */
    @Transactional
    public UserSummary createUser(String email, Password password, boolean admin) {
        User user = new User(
                EmailAddress.normalize(email), passwordEncoder.encode(password.value()), admin, clock.instant());
        User saved = userRepository.saveAndFlush(user);
        eventPublisher.publishEvent(new UserCreatedEvent(saved.getUserId()));
        return toSummary(saved);
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.getUserId(), user.getEmail(), user.isAdminFlag());
    }
}
