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
package cherry.mastersmith.auth.repository;

import cherry.mastersmith.auth.domain.LoginAttemptState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Repository;

/**
 * ロックの状態の DB アクセス（BR2.7、BR3.8、NFR1.5、NFR9.1）。呼び出し側（業務処理）のトランザクションの中で使う。
 *
 * <p>読み取りは行の排他つき（{@code SELECT ... FOR UPDATE}）で、待ちの上限は 3 秒。書き込みは JPA の変更の検出に頼らず、
 * 明示の更新の問い合わせを1回行う（値が変わらない場合も1回）。存在しないメールアドレスの試みは、ダミーの行のうちほかの試みが
 * 排他を持っていない行を、待たずに（{@code SKIP LOCKED}）排他つきで読む。
 */
@Repository
public class LoginAttemptStateRepository {

    /** 行の排他の待ちの上限（ミリ秒）。 */
    static final int LOCK_TIMEOUT_MILLIS = 3000;

    /** ダミーの行の数（ID は -1〜-8）。 */
    static final int DUMMY_ROWS = 8;

    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

    /** Hibernate の「排他を持つ行を飛ばす」の指定（{@code LockOptions.SKIP_LOCKED}）。 */
    private static final int SKIP_LOCKED = -2;

    private final EntityManager entityManager;

    /**
     * DB アクセスを作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public LoginAttemptStateRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 利用者の行を排他つきで読む（待ちの上限 3 秒）。
     *
     * @param subjectId 利用者ID
     * @return 状態（行が無ければ空）
     */
    public Optional<LoginAttemptState> lockForUpdate(long subjectId) {
        List<LoginAttemptState> rows = entityManager
                .createQuery("select s from LoginAttemptState s where s.subjectId = :id", LoginAttemptState.class)
                .setParameter("id", subjectId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint(LOCK_TIMEOUT_HINT, LOCK_TIMEOUT_MILLIS)
                .getResultList();
        return rows.stream().findFirst();
    }

    /**
     * ダミーの行のうち、ほかの試みが排他を持っていない行を1つ、待たずに排他つきで読む。すべて排他を持たれていれば、乱数で
     * 選んだ行を待ちの上限つきで読む。
     *
     * @return ダミーの行の状態
     */
    public LoginAttemptState lockDummyForUpdate() {
        List<LoginAttemptState> free = entityManager
                .createQuery(
                        "select s from LoginAttemptState s where s.subjectId < 0 order by s.subjectId desc",
                        LoginAttemptState.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint(LOCK_TIMEOUT_HINT, SKIP_LOCKED)
                .setMaxResults(1)
                .getResultList();
        if (!free.isEmpty()) {
            return free.getFirst();
        }
        long id = -1L - ThreadLocalRandom.current().nextInt(DUMMY_ROWS);
        return lockForUpdate(id).orElseThrow(() -> new IllegalStateException("ダミーの行がありません: " + id));
    }

    /**
     * 利用者の行が無ければ、失敗回数 0 の行を作る（既にあれば何もしない）。
     *
     * @param subjectId 利用者ID
     */
    public void createIfAbsent(long subjectId) {
        entityManager
                .createNativeQuery("MERGE INTO login_attempt_states t USING (VALUES (CAST(?1 AS BIGINT))) s(id)"
                        + " ON t.subject_id = s.id"
                        + " WHEN NOT MATCHED THEN INSERT (subject_id, consecutive_failures, locked_until)"
                        + " VALUES (s.id, 0, NULL)")
                .setParameter(1, subjectId)
                .executeUpdate();
    }

    /**
     * 状態を明示の更新の問い合わせ1回で書き込む（値が変わらない場合も1回行う）。
     *
     * @param subjectId 利用者ID、またはダミーの行の ID
     * @param consecutiveFailures 連続失敗回数
     * @param lockedUntil ロックの解除時刻（無ければ null）
     * @return 更新した行の数
     */
    public int update(long subjectId, int consecutiveFailures, Instant lockedUntil) {
        int updated = entityManager
                .createQuery("update LoginAttemptState s set s.consecutiveFailures = :failures,"
                        + " s.lockedUntil = :lockedUntil where s.subjectId = :id")
                .setParameter("failures", consecutiveFailures)
                .setParameter("lockedUntil", lockedUntil)
                .setParameter("id", subjectId)
                .executeUpdate();
        entityManager.clear();
        return updated;
    }
}
