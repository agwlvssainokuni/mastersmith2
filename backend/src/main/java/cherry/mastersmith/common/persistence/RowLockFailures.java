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

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;

/**
 * 行の排他の失敗（待ちの上限切れ・行き詰まり）の判定とログ（{@code security-design.md} 7.1、ND-3、SD-5）。
 *
 * <p>排他の失敗とみなす条件（どれか1つ）:
 *
 * <ol>
 *   <li>例外の原因の連なりに、JPA の {@link LockTimeoutException}・{@link PessimisticLockException}・{@link QueryTimeoutException}
 *       がある
 *   <li>原因の連なりに {@link SQLException} があり、誤りの番号が 50200（上限切れ）か 40001（行き詰まり）、または SQLState が
 *       {@code HYT00}・{@code 40001}
 * </ol>
 *
 * <p>型の写し方が Hibernate の版で変わっても受けられるよう、型と誤りの番号の両方で見分ける。Spring の例外の変換（例
 * {@code CannotAcquireLockException}）の後の連なりも、原因を最後までたどって見る。
 */
public final class RowLockFailures {

    /** 排他の失敗とみなす誤りの番号（H2 の上限切れ 50200、行き詰まり 40001）。 */
    static final Set<Integer> ERROR_CODES = Set.of(50200, 40001);

    /** 排他の失敗とみなす SQLState（上限切れ HYT00、行き詰まり 40001）。 */
    static final Set<String> SQL_STATES = Set.of("HYT00", "40001");

    /** 排他を取れなかったときの WARN の決まった文。 */
    static final String WARN_MESSAGE = "行の排他を取れませんでした";

    private RowLockFailures() {}

    /**
     * 例外が排他の失敗（待ちの上限切れ・行き詰まり）かを判定する。
     *
     * @param failure 例外（null なら偽）
     * @return 排他の失敗なら真
     */
    public static boolean isLockFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (isLockFailureItself(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLockFailureItself(Throwable current) {
        if (current instanceof LockTimeoutException
                || current instanceof PessimisticLockException
                || current instanceof QueryTimeoutException) {
            return true;
        }
        if (current instanceof SQLException sql) {
            return ERROR_CODES.contains(sql.getErrorCode())
                    || (sql.getSQLState() != null && SQL_STATES.contains(sql.getSQLState()));
        }
        return false;
    }

    /**
     * 排他を取れなかったことを WARN で出す。キーと値は排他の種類（{@code lockKind}）と例外のクラスの名前（{@code exceptionClass}）
     * だけで、例外そのもの（文・原因・スタックトレース）はログに渡さない（連なりの文に行の値が入りうるため）。
     *
     * @param logger 出す先のロガー
     * @param lockKind 排他の種類（例 {@code LOGIN_ATTEMPT_ROW}）
     * @param failure 排他の失敗の例外（クラスの名前だけを使う）
     */
    public static void warn(Logger logger, String lockKind, Throwable failure) {
        logger.atWarn()
                .addKeyValue("lockKind", lockKind)
                .addKeyValue("exceptionClass", failure.getClass().getName())
                .log(WARN_MESSAGE);
    }
}
