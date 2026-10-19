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
package cherry.mastersmith.targetdb.domain;

import java.io.Serial;

/**
 * 業務データの読み取りの口に渡した処理が、想定外の非検査の例外を投げたことを表す（Intent 261016-business-data-views の U1、NFR 設計
 * のレビュー R-02）。
 *
 * <p>元の例外のメッセージと原因は、行の値を含みうるため持たない。元の例外のクラスの単純名だけを持つ。
 */
public final class TargetReadUnexpectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String exceptionClass;

    /**
     * 作る。
     *
     * @param exceptionClass 元の例外のクラスの単純名
     */
    public TargetReadUnexpectedException(String exceptionClass) {
        super(null, null, false, true);
        this.exceptionClass = exceptionClass;
    }

    /**
     * 元の例外のクラスの単純名を返す。
     *
     * @return クラスの単純名
     */
    public String exceptionClass() {
        return exceptionClass;
    }
}
