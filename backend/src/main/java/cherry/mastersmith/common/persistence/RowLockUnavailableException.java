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

/**
 * 行の排他を取れなかった（待ちの上限切れ・行き詰まり）ことを表す、値を含まない例外（{@code security-design.md} 7.2）。
 *
 * <p>元の例外の連なりの文に排他されていた行の値が入りうるため、決まった文だけを持ち、原因をつながない（後から原因を付けることも、
 * 抑えた例外を足すこともできない）。持ってよいのは排他の種類（{@code lockKind}、定数の名前）だけ。
 *
 * <p>想定外の誤りとして扱う（{@code GlobalExceptionHandler} が 500 {@code INTERNAL_ERROR} にする）。同時の重なりの捕まえ直しに
 * 当たらないよう、Spring の {@code DataIntegrityViolationException} などの DB の例外の系統にしない。
 */
public class RowLockUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 決まった文。 */
    static final String MESSAGE = "行の排他を取れませんでした（待ちの上限切れ・行き詰まり）";

    private final String lockKind;

    /**
     * 例外を作る。
     *
     * @param lockKind 排他の種類（例 {@code LOGIN_ATTEMPT_ROW}）
     */
    public RowLockUnavailableException(String lockKind) {
        super(MESSAGE, null, false, true);
        this.lockKind = lockKind;
    }

    /**
     * 排他の種類を返す。
     *
     * @return 排他の種類
     */
    public String getLockKind() {
        return lockKind;
    }
}
