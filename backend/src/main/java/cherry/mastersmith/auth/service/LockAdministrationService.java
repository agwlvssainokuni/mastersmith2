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
package cherry.mastersmith.auth.service;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.common.persistence.RowLockAttempt;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication の、利用者の管理に向けた口（Intent 260930-user-admin の U3、契約 C8 の Authentication の口）。
 *
 * <p>一覧のロックの判定（{@link #lockViewsOf(Collection)}、B3）と、失敗回数を戻す操作の2段の口（{@link #prepareFailureReset(long)}・
 * {@link #completeFailureReset(long)}、B4。FS の D6）を持つ。失敗回数そのものとダミーの行は外へ渡さない（BR1.7・BR4.5）。
 */
@Service
public class LockAdministrationService {

    private final LoginAttemptStateRepository repository;

    private final Clock clock;

    private final LoginAttemptBarrier barrier;

    /**
     * 作る。
     *
     * @param repository ロックの状態の DB アクセス
     * @param clock 時計
     * @param barrier ロックの状態の行を排他した直後の待ち合わせの口
     */
    public LockAdministrationService(LoginAttemptStateRepository repository, Clock clock, LoginAttemptBarrier barrier) {
        this.repository = repository;
        this.clock = clock;
        this.barrier = barrier;
    }

    /**
     * 利用者ごとのロックの判定の結果を返す（BR1.7）。排他なしの1回の問い合わせで読み、今の時刻は注入した時計から取る。
     *
     * <p>呼び出し元の読み取りだけのトランザクションに入る（無ければ新しく始める）。行の無い利用者は {@link LockView#NONE}。空の集まりでは
     * 問い合わせない。
     *
     * @param userIds 利用者 ID（正の値）
     * @return 利用者 ID ごとの判定（渡した ID をすべて含む。順は渡した順）
     * @throws IllegalArgumentException 正でない ID があるとき（ダミーの行を読まないため）
     */
    @Transactional(readOnly = true)
    public Map<Long, LockView> lockViewsOf(Collection<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        if (userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("利用者 ID は正の値です");
        }
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, LoginAttemptState> rows = repository.findBySubjectIds(ids).stream()
                .collect(Collectors.toMap(LoginAttemptState::getSubjectId, Function.identity()));
        Instant now = clock.instant();
        Map<Long, LockView> views = new LinkedHashMap<>();
        for (Long id : ids) {
            views.put(id, LockView.of(rows.get(id), now));
        }
        return views;
    }

    /**
     * 失敗回数を戻す操作の1段目（契約 C8、FS の D6、BR3.4・BR4.5）。対象のロックの状態の行だけを、ログインの判定と同じ排他（待ちの
     * 上限 3000 ミリ秒）で読み、戻せるかを判定する。行を作らない。
     *
     * <p>呼び出し元のトランザクションの中でだけ呼べる（無ければ {@code IllegalTransactionStateException}）。行を排他できたときは、その直後に
     * 待ち合わせの口を通る（BR3.6。行が無いときは排他した行が無いため通らない）。排他を取れなかったときは例外ではなく
     * {@link LoginFailureResetPreparation.Busy} を返す（巻き戻しの印は呼び出し元が付ける。BR3.5）。
     *
     * @param userId 対象の利用者 ID（正の値。呼び出し元が利用者の表でいると確かめた ID）
     * @return 戻せる・戻せない・排他を取れなかった
     * @throws IllegalArgumentException 正でない ID のとき（ダミーの行に触れないため）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoginFailureResetPreparation prepareFailureReset(long userId) {
        requirePositive(userId);
        return switch (repository.tryLockForUpdate(userId)) {
            case RowLockAttempt.Busy<Optional<LoginAttemptState>> busy -> new LoginFailureResetPreparation.Busy();
            case RowLockAttempt.Acquired<Optional<LoginAttemptState>> acquired -> prepared(userId, acquired.value());
        };
    }

    /**
     * 失敗回数を戻す操作の2段目（契約 C8、FS の D6、BR4.5）。失敗回数 0・解除の予定の時刻なしを、明示の更新の問い合わせ1回で書く。
     * 行を作らない。
     *
     * <p>呼び出し元のトランザクションの中で、1段目が {@link LoginFailureResetPreparation.Ready} を返した後にだけ呼ぶ。
     *
     * @param userId 対象の利用者 ID（正の値）
     * @throws IllegalArgumentException 正でない ID のとき
     * @throws IllegalStateException 行が無かったとき（1段目の後には起きない想定外の誤り。メッセージには利用者 ID だけを載せる）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeFailureReset(long userId) {
        requirePositive(userId);
        if (repository.update(userId, 0, null) == 0) {
            throw new IllegalStateException("ロックの状態の行がありません: userId=" + userId);
        }
    }

    private LoginFailureResetPreparation prepared(long userId, Optional<LoginAttemptState> row) {
        if (row.isEmpty()) {
            return new LoginFailureResetPreparation.NothingToReset();
        }
        barrier.afterLock(userId);
        // 戻せるかは一覧の表示（LockView の resettable）と同じ定義で判定する（R-05）。
        return LockView.of(row.get(), clock.instant()).resettable()
                ? new LoginFailureResetPreparation.Ready()
                : new LoginFailureResetPreparation.NothingToReset();
    }

    private static void requirePositive(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("利用者 ID は正の値です");
        }
    }
}
