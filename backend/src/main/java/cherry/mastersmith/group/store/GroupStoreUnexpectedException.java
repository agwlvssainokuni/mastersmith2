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
package cherry.mastersmith.group.store;

import java.io.Serial;

/**
 * グループの排他と書き込みでの想定外の DB の失敗（{@code security-design.md} 4.1、BR9.3）。
 *
 * <p>元の例外のクラスの名前だけを持ち、元の例外（原因）・文・SQLState・制約の名前を持たない（元の連なりの文に行の値が入るため。計画の
 * 11節 Q5: A、D-22）。既存の {@code @RestControllerAdvice} が想定外の失敗として ERROR で1回出し、500 を返す。
 */
public class GroupStoreUnexpectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 包み直した元の例外のクラスの名前。 */
    private final String originalClass;

    /**
     * 作る。
     *
     * @param original 元の例外（クラスの名前だけを使う）
     */
    public GroupStoreUnexpectedException(Throwable original) {
        super("グループの排他と書き込みで想定外の DB の失敗が起きました: " + original.getClass().getName());
        this.originalClass = original.getClass().getName();
    }

    /**
     * 包み直した元の例外のクラスの名前を返す。
     *
     * @return クラスの名前
     */
    public String getOriginalClass() {
        return originalClass;
    }
}
