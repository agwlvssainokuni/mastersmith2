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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.ReferenceRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 参照先の行1つの応答（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の {@code ReferenceRows.rows[]}）。値・ラベル・セルは
 * 加工せずに文字列で返す（画面が文字として出す）。文字列にするときは値・ラベル・セルを伏せる（BR10.1）。
 *
 * @param value 値（切らない）
 * @param label ラベル（NULL・バイナリ・password は null）
 * @param cells 一覧の列の値（null を含む。ラベルの1件読みでは空）
 */
public record ReferenceRowResponse(
        String value,
        @JsonInclude(JsonInclude.Include.ALWAYS) String label,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<String> cells) {

    /**
     * 行から作る。
     *
     * @param row 行
     * @return 応答
     */
    static ReferenceRowResponse from(ReferenceRow row) {
        return new ReferenceRowResponse(row.value(), row.label(), row.cells());
    }

    /** 値・ラベル・セルを伏せる（BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceRowResponse[value=***, label=" + (label == null ? "null" : "***") + ", cells="
                + (cells == null ? 0 : cells.size()) + "]";
    }
}
