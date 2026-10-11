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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.domain.TransferError;

/**
 * 権限の YAML の確かめ・適用の拒否（{@code ROLE_TRANSFER_INVALID}）の応答の {@code errors} の1件（BR9.5）。入れた値は載せない。
 *
 * <p>場所はロールと対象の名前を含みうるため、{@link #toString()} は理由と位置だけを出す（共通の誤りの応答の変換が応答の中身を TRACE に
 * 出すため。NFR1.9）。応答の JSON には4つの項目がそのまま入る。
 *
 * @param reason 理由
 * @param line 行（無ければ null）
 * @param column 列（無ければ null）
 * @param path YAML の中の場所（キーを点でつないだ形。無ければ null）
 */
public record TransferErrorItem(String reason, Integer line, Integer column, String path) {

    /**
     * 誤りから作る。
     *
     * @param error 誤り
     * @return 応答の1件
     */
    static TransferErrorItem from(TransferError error) {
        return new TransferErrorItem(error.reason().name(), error.line(), error.column(), error.path());
    }

    /** 理由と位置だけを出す。 */
    @Override
    public String toString() {
        return "TransferErrorItem[reason=" + reason + ", line=" + line + ", column=" + column + "]";
    }
}
