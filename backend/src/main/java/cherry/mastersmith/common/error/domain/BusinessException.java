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
package cherry.mastersmith.common.error.domain;

import java.util.Objects;

/**
 * 想定内のエラー（業務エラー）。起こすと、共通のエラー応答の変換で、持っている問題の種類の状態コードと code の
 * ErrorResponse になる（BR5.16）。
 *
 * <p>{@code detail} には利用者に見せてよい説明だけを入れる。内部の情報・秘密情報を入れない（BR5.4）。例外のメッセージには
 * code だけを入れる。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ProblemType problemType;

    private final String detail;

    /**
     * 問題の種類だけを持つ業務エラーを作る。
     *
     * @param problemType 問題の種類
     */
    public BusinessException(ProblemType problemType) {
        this(problemType, null);
    }

    /**
     * 利用者に見せてよい説明を持つ業務エラーを作る。
     *
     * @param problemType 問題の種類
     * @param detail 利用者に見せてよい説明（無ければ null）
     */
    public BusinessException(ProblemType problemType, String detail) {
        super(Objects.requireNonNull(problemType, "problemType").code());
        this.problemType = problemType;
        this.detail = detail;
    }

    /**
     * 問題の種類を返す。
     *
     * @return 問題の種類
     */
    public ProblemType getProblemType() {
        return problemType;
    }

    /**
     * 利用者に見せてよい説明を返す。
     *
     * @return 説明（無ければ null）
     */
    public String getDetail() {
        return detail;
    }
}
