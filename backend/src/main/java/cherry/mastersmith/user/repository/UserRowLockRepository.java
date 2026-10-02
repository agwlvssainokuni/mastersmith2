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
package cherry.mastersmith.user.repository;

import cherry.mastersmith.common.persistence.RowLockAttempt;
import cherry.mastersmith.common.persistence.RowLockFailures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * 利用者の行の排他の DB アクセス（Intent 260930-user-admin の U3、契約 C8 の lockAdminRowsInIdOrder・lockUserRow の下回り、BR3.1・
 * BR3.3、{@code reliability-design.md} 2.1・5.2）。呼び出し元（業務処理）のトランザクションの中で使う。
 *
 * <p>排他の問い合わせは利用者 ID だけを読み、エンティティを持続化の文脈に載せない（排他の後の値は呼び出し元が別の問い合わせで投影として
 * 読む）。待ちの上限は既存の排他と同じ 3000 ミリ秒（Hibernate は {@code for update wait 3} として出す）。
 *
 * <p>排他の待ちの上限切れ・行き詰まりは、問い合わせを実行するこのクラスのメソッドの本体の中で受け、例外をそのまま外へ出さない
 * （{@code reliability-design.md} 5.2、ND-1）。例外の連なりの文に排他されていた行の全部の列の値（メールアドレス・パスワードのハッシュ値・
 * 氏名など）が入りうるため、WARN には排他の種類と例外のクラスの名前だけを出し、結果を Busy で返す。排他の失敗でない例外はそのまま
 * 投げる。Spring Data のインターフェースの {@code @Lock} の問い合わせは、例外が代理の外へ出るため使わない。
 */
@Repository
public class UserRowLockRepository {

    /** 行の排他の待ちの上限（ミリ秒。既存の排他と同じ値。NFR4.4）。 */
    static final int LOCK_TIMEOUT_MILLIS = 3000;

    /** 管理者の行と対象の行の排他の種類（ログの {@code lockKind}）。 */
    static final String ADMIN_ROWS = "ADMIN_ROWS";

    /** 対象の利用者の行だけの排他の種類（ログの {@code lockKind}）。 */
    static final String USER_ROW = "USER_ROW";

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRowLockRepository.class);

    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

    private final EntityManager entityManager;

    /**
     * DB アクセスを作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public UserRowLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 管理者の印を持つすべての行（停止中の管理者を含む）と対象の行を、利用者 ID の昇順に排他する（BR3.1）。
     *
     * <p>H2 は主キーの走査の順に1行ずつ排他するため、どの操作も同じ順で取り、互いの行を待ち合っても行き詰まらない
     * （{@code reliability-design.md} 1.4・4節）。対象がいなくても管理者の行は排他する。
     *
     * @param targetUserId 対象の利用者 ID
     * @return 取れたら排他した行の利用者 ID（昇順）、取れなければ Busy
     */
    public RowLockAttempt<List<Long>> lockAdminRowsAndTarget(long targetUserId) {
        return lockIds(
                ADMIN_ROWS,
                "select u.userId from User u where u.adminFlag = true or u.userId = :target order by u.userId",
                query -> query.setParameter("target", targetUserId));
    }

    /**
     * 対象の利用者の行だけを排他する（BR3.3。停止を解く操作）。
     *
     * @param userId 対象の利用者 ID
     * @return 取れたら対象がいたか、取れなければ Busy
     */
    public RowLockAttempt<Boolean> lockUserRow(long userId) {
        return switch (lockIds(
                USER_ROW,
                "select u.userId from User u where u.userId = :userId",
                query -> query.setParameter("userId", userId))) {
            case RowLockAttempt.Acquired<List<Long>> acquired ->
                RowLockAttempt.acquired(!acquired.value().isEmpty());
            case RowLockAttempt.Busy<List<Long>> busy -> RowLockAttempt.busy();
        };
    }

    /** 利用者 ID だけを読む問い合わせを排他つきで流し、排他の失敗を Busy に変える。 */
    private RowLockAttempt<List<Long>> lockIds(
            String lockKind, String jpql, UnaryOperator<TypedQuery<Long>> parameters) {
        try {
            TypedQuery<Long> query = entityManager
                    .createQuery(jpql, Long.class)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint(LOCK_TIMEOUT_HINT, LOCK_TIMEOUT_MILLIS);
            return RowLockAttempt.acquired(List.copyOf(parameters.apply(query).getResultList()));
        } catch (PersistenceException e) {
            if (!RowLockFailures.isLockFailure(e)) {
                throw e;
            }
            RowLockFailures.warn(LOGGER, lockKind, e);
            return RowLockAttempt.busy();
        }
    }
}
