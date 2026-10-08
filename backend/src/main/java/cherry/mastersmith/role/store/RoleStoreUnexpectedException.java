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
package cherry.mastersmith.role.store;

import java.io.Serial;

/**
 * ロールの排他と書き込みでの想定外の DB の失敗（{@code security-design.md} 4.1、BR12.3、計画の 13節 Q5: A・D-7）。
 *
 * <p>元の例外（原因）と文を持たない（元の連なりの文に行の値が入るため）。文には元の例外のクラスの名前、SQLState、role の既知の制約の
 * 名前の一覧に当たった名前だけを入れる。既存の {@code @RestControllerAdvice} が想定外の失敗として ERROR で1回出し、500 を返すため、
 * その ERROR の文とスタックトレースで手がかりが見える（{@code common} は変えない）。
 */
public class RoleStoreUnexpectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 包み直した元の例外のクラスの名前。 */
    private final String originalClass;

    /** 連なりの SQLState（無ければ null）。 */
    private final String sqlState;

    /** role の既知の制約の名前の一覧に当たった名前（無ければ null）。 */
    private final String constraintName;

    /**
     * 作る。
     *
     * @param original 元の例外（クラスの名前だけを使う）
     * @param sqlState 連なりの SQLState（無ければ null）
     * @param constraintName role の既知の制約の名前（無ければ null）
     */
    public RoleStoreUnexpectedException(Throwable original, String sqlState, String constraintName) {
        super("ロールの排他と書き込みで想定外の DB の失敗が起きました: class=" + original.getClass().getName() + ", sqlState=" + sqlState
                + ", constraint=" + constraintName);
        this.originalClass = original.getClass().getName();
        this.sqlState = sqlState;
        this.constraintName = constraintName;
    }

    /**
     * 包み直した元の例外のクラスの名前を返す。
     *
     * @return クラスの名前
     */
    public String getOriginalClass() {
        return originalClass;
    }

    /**
     * 連なりの SQLState を返す。
     *
     * @return SQLState（無ければ null）
     */
    public String getSqlState() {
        return sqlState;
    }

    /**
     * role の既知の制約の名前の一覧に当たった名前を返す。
     *
     * @return 制約の名前（無ければ null）
     */
    public String getConstraintName() {
        return constraintName;
    }
}
