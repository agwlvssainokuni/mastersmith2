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
package cherry.mastersmith.common.persistence;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.hibernate.Session;

/**
 * 「あれば更新・無ければ挿入」の共通の手順（H2 と PostgreSQL の両方に通る書き方）。
 *
 * <ol>
 *   <li>更新を流し、1 行以上に当たれば {@link UpsertOutcome#UPDATED}
 *   <li>0 行ならセーブポイントを張って挿入を流し、通れば {@link UpsertOutcome#INSERTED}
 *   <li>挿入が鍵の重なり（{@link #isKeyConflict}）なら、セーブポイントまで戻して同じ更新をやり直し、1 行以上に当たれば
 *       {@link UpsertOutcome#UPDATED_AFTER_CONFLICT}、0 行なら {@link KeyedUpsertLostException}
 *   <li>重なりでない {@link SQLException} は、セーブポイントまで戻した後にそのまま外へ投げる
 * </ol>
 *
 * <p>呼び出し元のトランザクションの中の同じ接続で動き（{@link Session#doReturningWork}）、書く前に {@link EntityManager#flush()}
 * を呼ぶ。挿入は必ず JDBC で行い、ネイティブの問い合わせの {@code executeUpdate} にしない（重なりの {@link SQLException} を
 * ここで受けるため）。値は位置の引数で束縛し、{@code byte[]} は複製せずに渡す。値・例外の文はログにも例外にも出さない。
 *
 * <p>Bean にせず、持ち主（repository・store）がコンストラクターで作って持つ。機能のパッケージには依存しない。
 */
public final class KeyedUpsertExecutor {

    /** 例外の連なりをたどる段数の上限。 */
    private static final int MAX_CHAIN_DEPTH = 10;

    /** 一意の鍵の違反の SQLState。 */
    private static final String UNIQUE_VIOLATION = "23505";

    private final EntityManager entityManager;

    /**
     * 作る。
     *
     * @param entityManager JPA のエンティティの管理（同じトランザクションの接続を借りる）
     */
    public KeyedUpsertExecutor(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    /**
     * 行を書く（あれば更新、無ければ挿入）。
     *
     * @param upsert 表の定義
     * @param updateArguments 更新の文の引数（値はすべて非 null）
     * @param insertArguments 挿入の文の引数（値はすべて非 null）
     * @return どの経路で書いたか
     * @throws KeyedUpsertLostException 重なりの後の更新が 0 行だったとき
     */
    public UpsertOutcome upsert(KeyedUpsert upsert, Object[] updateArguments, Object[] insertArguments) {
        Objects.requireNonNull(upsert, "upsert");
        Objects.requireNonNull(updateArguments, "updateArguments");
        Objects.requireNonNull(insertArguments, "insertArguments");
        entityManager.flush();
        return entityManager
                .unwrap(Session.class)
                .doReturningWork(connection -> execute(connection, upsert, updateArguments, insertArguments));
    }

    /**
     * 手順の本体（単体テストが本物の接続で直接呼ぶ）。トランザクションの中（自動確定でない）の接続で呼ぶ。
     *
     * @param connection 接続
     * @param upsert 表の定義
     * @param updateArguments 更新の文の引数
     * @param insertArguments 挿入の文の引数
     * @return どの経路で書いたか
     * @throws SQLException 重なりでない失敗（セーブポイントまで戻した後）
     */
    static UpsertOutcome execute(
            Connection connection, KeyedUpsert upsert, Object[] updateArguments, Object[] insertArguments)
            throws SQLException {
        if (run(connection, upsert.updateSql(), updateArguments) > 0) {
            return UpsertOutcome.UPDATED;
        }
        Savepoint savepoint = connection.setSavepoint();
        try {
            run(connection, upsert.insertSql(), insertArguments);
        } catch (SQLException failure) {
            rollbackTo(connection, savepoint, failure);
            if (!isKeyConflict(upsert, failure)) {
                throw failure;
            }
            if (run(connection, upsert.updateSql(), updateArguments) > 0) {
                return UpsertOutcome.UPDATED_AFTER_CONFLICT;
            }
            throw new KeyedUpsertLostException(upsert);
        }
        connection.releaseSavepoint(savepoint);
        return UpsertOutcome.INSERTED;
    }

    /**
     * 失敗が、この表の鍵の重なりかを判定する（SQLState {@code 23505}、かつ文を大文字にして重なりの印を含み、重なりでない印を含まない。
     * 例外の連なりを 10 段までたどる）。文は読むだけで、保持も出力もしない。
     *
     * @param upsert 表の定義
     * @param failure 失敗（null なら偽）
     * @return 鍵の重なりなら真
     */
    static boolean isKeyConflict(KeyedUpsert upsert, Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int depth = 0;
        for (Throwable current = failure;
                current != null && depth < MAX_CHAIN_DEPTH && seen.add(current);
                current = current.getCause(), depth++) {
            if (current instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                String message = sql.getMessage();
                if (message == null) {
                    continue;
                }
                String upper = message.toUpperCase(Locale.ROOT);
                if (upper.contains(upsert.conflictMarker())
                        && (upsert.notConflictMarker() == null || !upper.contains(upsert.notConflictMarker()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int run(Connection connection, String sql, Object[] arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                Object value = arguments[i];
                if (value instanceof byte[] bytes) {
                    statement.setBytes(i + 1, bytes);
                } else {
                    statement.setObject(i + 1, value);
                }
            }
            return statement.executeUpdate();
        }
    }

    private static void rollbackTo(Connection connection, Savepoint savepoint, SQLException failure) {
        try {
            connection.rollback(savepoint);
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
