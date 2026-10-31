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
package cherry.mastersmith.businessdata.service;

import java.io.Serial;

/**
 * 業務データの読み取りの想定外の失敗（500。logical-components.md の L3）。行の値・JDBC の例外の文を含みうるため、メッセージも原因も
 * 持たない（BR10.2）。手がかり（SQLState・業者の番号・例外のクラスの名前）は、投げる前に業務処理の層が WARN で1行出す。
 *
 * <p>保存の前の参照の確かめ（⑩）の中で投げるものは、借りた接続の口の外で WARN を出すために、値を含まない手がかりだけを持つ
 * （{@link #withDiagnosis}。Intent 261029-reference-lookup-cleanup の直しの Bolt B7、U2 の NFR1.32）。手がかりは文字列にしない
 * （{@code toString} はクラスの名前だけ）。
 */
public final class BusinessDataUnexpectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SQLState（無ければ null）。 */
    private final String sqlState;

    /** 業者のエラーの番号（無ければ null）。 */
    private final Integer vendorCode;

    /** 元の例外のクラスの名前（無ければ null）。 */
    private final String exceptionClass;

    /** 作る（手がかりを持たない）。 */
    public BusinessDataUnexpectedException() {
        this(null, null, null);
    }

    private BusinessDataUnexpectedException(String sqlState, Integer vendorCode, String exceptionClass) {
        super(null, null, false, true);
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
        this.exceptionClass = exceptionClass;
    }

    /**
     * 値を含まない手がかりを持つものを作る（メッセージも原因も持たない）。
     *
     * @param sqlState SQLState（無ければ null）
     * @param vendorCode 業者のエラーの番号（無ければ null）
     * @param exceptionClass 元の例外のクラスの名前（無ければ null）
     * @return 例外
     */
    static BusinessDataUnexpectedException withDiagnosis(String sqlState, Integer vendorCode, String exceptionClass) {
        return new BusinessDataUnexpectedException(sqlState, vendorCode, exceptionClass);
    }

    /** SQLState（無ければ null）。 */
    String sqlState() {
        return sqlState;
    }

    /** 業者のエラーの番号（無ければ null）。 */
    Integer vendorCode() {
        return vendorCode;
    }

    /** 元の例外のクラスの名前（無ければ null）。 */
    String exceptionClass() {
        return exceptionClass;
    }
}
