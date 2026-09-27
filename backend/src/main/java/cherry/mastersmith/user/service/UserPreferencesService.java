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

import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.PasswordChangeFailureReason;
import cherry.mastersmith.user.domain.PasswordChangeValidation;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import cherry.mastersmith.user.domain.PasswordHash;
import cherry.mastersmith.user.domain.PasswordPolicy;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.PreferencesValidation;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログインした利用者の自分の設定（契約 C4）の業務処理: プリファレンスの取得・保存と、パスワードの変更（ADR-004）。
 *
 * <ul>
 *   <li>入力の検証は DB を使わない純粋な関数で先に済ませ、誤りがあれば内部DB に触れない（BR3.2・BR4.1）
 *   <li>トランザクションの境界は {@link TransactionTemplate} で短く取る。パスワードの照合と新しいハッシュの計算（bcrypt）は
 *       トランザクションの外で行い、その間は内部DB の接続を持たない（NFR5.1、{@code performance-design.md} 2節）
 *   <li>書き込みは書き換える列だけの更新の問い合わせで行う。パスワードは読んだときのハッシュのままなら書き換える条件つきの更新とし、
 *       照合の後にほかの変更が確定していれば今のパスワードの誤りにする（{@code reliability-design.md} 2節・3節）
 *   <li>パスワードの変更の成功と今のパスワードの誤りだけ出来事を知らせ（監査はその受け取り側）、プリファレンスの保存では知らせない
 *       （BR3.5・BR7.2・BR7.3）。リフレッシュトークン・アクセストークン・ロックの状態には触れない（BR4.5）
 *   <li>HTTP の状態は知らず、結果の型で返す。成功の場面で新しいログを出さない（{@code observability-design.md} 2節）
 * </ul>
 */
@Service
public class UserPreferencesService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final PasswordChangeBarrier barrier;

    private final TransactionTemplate transaction;

    private final TransactionTemplate readOnlyTransaction;

    /**
     * 業務処理を作る。
     *
     * @param userRepository 利用者の DB アクセス
     * @param passwordEncoder パスワードのハッシュの仕組み（照合と新しいハッシュ）
     * @param eventPublisher 出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     * @param barrier 照合の後・書き込みの前の待ち合わせの口（本番は何もしない）
     */
    public UserPreferencesService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager,
            PasswordChangeBarrier barrier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.barrier = barrier;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * 本人の氏名と表示の設定を読む（BR3.1。要求ごとに内部DB から読み、テーマ system は解決せずに返す）。
     *
     * @param userId 本人の利用者 ID
     * @return 結果（{@link PreferencesResult.Ok} か {@link PreferencesResult.UserNotFound}）
     */
    public PreferencesResult getPreferences(long userId) {
        Optional<Preferences> found = readOnlyTransaction.execute(
                status -> userRepository.findById(userId).map(User::getPreferences));
        if (found == null || found.isEmpty()) {
            return new PreferencesResult.UserNotFound();
        }
        return new PreferencesResult.Ok(found.get());
    }

    /**
     * 本人の氏名と表示の設定の4つを、まとめて検証して置き換える（BR3.2〜BR3.5）。
     *
     * @param userId 本人の利用者 ID
     * @param command 保存の入力
     * @return 結果（保存した値、入力の誤り、または本人がいない）
     */
    public PreferencesResult savePreferences(long userId, PreferencesCommand command) {
        List<FieldError> errors = PreferencesValidation.validate(
                command.displayName(), command.language(), command.theme(), command.fontSize());
        if (!errors.isEmpty()) {
            return new PreferencesResult.Invalid(errors);
        }
        Preferences preferences = PreferencesValidation.toPreferences(
                command.displayName(), command.language(), command.theme(), command.fontSize());
        Integer rows = transaction.execute(status -> userRepository.updatePreferences(userId, preferences));
        if (rows == null || rows == 0) {
            return new PreferencesResult.UserNotFound();
        }
        return new PreferencesResult.Ok(preferences);
    }

    /**
     * 今のパスワードを確かめて、本人のパスワードを変える（BR4.1〜BR4.5、{@code performance-design.md} 2節の流れ）。
     *
     * @param userId 本人の利用者 ID
     * @param command 変更の入力
     * @param origin 要求の送り手の情報（監査に載せる）
     * @return 結果（変えた、入力の誤り、今のパスワードの誤り、または本人がいない）
     */
    public PasswordChangeResult changePassword(long userId, PasswordChangeCommand command, RequestOrigin origin) {
        List<FieldError> errors = PasswordChangeValidation.validate(
                command.currentPassword().value(),
                command.newPassword().value(),
                command.newPasswordConfirmation().value());
        if (!errors.isEmpty()) {
            return new PasswordChangeResult.Invalid(errors);
        }
        Optional<PasswordHash> stored = readOnlyTransaction.execute(
                status -> userRepository.findById(userId).map(user -> new PasswordHash(user.getPasswordHash())));
        if (stored == null || stored.isEmpty()) {
            return new PasswordChangeResult.UserNotFound();
        }
        if (!matchesCurrent(command, stored.get())) {
            publishMismatch(userId, origin);
            return new PasswordChangeResult.CurrentMismatch();
        }
        PasswordHash newHash =
                new PasswordHash(passwordEncoder.encode(command.newPassword().value()));
        barrier.beforeWrite(userId);
        WriteOutcome outcome = transaction.execute(status -> write(userId, stored.get(), newHash, origin));
        if (outcome == WriteOutcome.CHANGED) {
            return new PasswordChangeResult.Changed();
        }
        if (outcome == WriteOutcome.CHANGED_BY_OTHER) {
            // 照合の後にほかの変更が確定した。トランザクションを終えた後に、今のパスワードの誤りとして知らせる。
            publishMismatch(userId, origin);
            return new PasswordChangeResult.CurrentMismatch();
        }
        return new PasswordChangeResult.UserNotFound();
    }

    /** 今のパスワードを照合する。72 バイトを超える値は照合の仕組みに渡さず不一致とする（BR4.2、NFR9.1）。 */
    private boolean matchesCurrent(PasswordChangeCommand command, PasswordHash stored) {
        String current = command.currentPassword().value();
        return PasswordPolicy.fitsMaxBytes(current) && passwordEncoder.matches(current, stored.value());
    }

    /**
     * 読んだときのハッシュのままなら新しいハッシュを書き込み、同じトランザクションで成功を知らせる（確定の後に監査が記録される）。
     * 書き換えられなければ本人がいるかを読み直す（{@code reliability-design.md} 2節）。
     */
    private WriteOutcome write(long userId, PasswordHash readHash, PasswordHash newHash, RequestOrigin origin) {
        int rows = userRepository.updatePasswordHashIfUnchanged(userId, readHash, newHash);
        if (rows == 1) {
            eventPublisher.publishEvent(PasswordChangedEvent.succeeded(userId, clock.instant(), origin));
            return WriteOutcome.CHANGED;
        }
        return userRepository.existsById(userId) ? WriteOutcome.CHANGED_BY_OTHER : WriteOutcome.USER_NOT_FOUND;
    }

    /** 今のパスワードの誤りを知らせる（トランザクションの外。監査はその場で記録される。BR7.3）。 */
    private void publishMismatch(long userId, RequestOrigin origin) {
        eventPublisher.publishEvent(PasswordChangedEvent.failed(
                userId, PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH, clock.instant(), origin));
    }

    /** 新しいハッシュの書き込みの結果。 */
    private enum WriteOutcome {
        CHANGED,
        CHANGED_BY_OTHER,
        USER_NOT_FOUND
    }
}
