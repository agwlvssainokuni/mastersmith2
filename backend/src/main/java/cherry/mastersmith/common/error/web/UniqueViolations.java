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
package cherry.mastersmith.common.error.web;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 想定外の誤りの例外の連なりに、一意の制約の違反があるかの判定（Intent 261003-user-admin-followup の FR8.2、案 C の B）。
 *
 * <p>違反とみなす条件（どれか1つ）:
 *
 * <ol>
 *   <li>例外の原因の連なりに、Spring の {@link DataIntegrityViolationException} か Hibernate の
 *       {@link ConstraintViolationException} がある
 *   <li>原因の連なりに {@link SQLException} があり、SQLState が一意の制約の違反の {@code 23505}
 * </ol>
 *
 * <p>連なりの文（H2 の違反の文）には重なった値（メールアドレスなど）が入るため、当たったときは例外の文と原因の連なりをログに渡さず、
 * クラスの名前だけを出す。行の排他の失敗（{@code RowLockFailures}）と同じ扱いにそろえる。型の写し方が版で変わっても受けられるよう、
 * 型と SQLState の両方で見分ける。
 */
final class UniqueViolations {

    /** 一意の制約の違反の SQLState。 */
    static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private UniqueViolations() {}

    /**
     * 例外の連なりに一意の制約の違反があるかを判定する。
     *
     * @param failure 例外（null なら偽）
     * @return 違反があれば真
     */
    static boolean isUniqueViolation(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof DataIntegrityViolationException || current instanceof ConstraintViolationException) {
                return true;
            }
            if (current instanceof SQLException sql && UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
