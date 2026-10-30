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

import cherry.mastersmith.businessdata.domain.ReferenceFailureReason;
import cherry.mastersmith.businessdata.domain.ReferenceRows;
import java.util.Objects;

/**
 * 参照先の検索とラベルの1件読みの結果（Intent 261029-reference-lookup-cleanup の U1、BR1.2・BR1.3・BR1.5・BR5.6・BR6.4）。画面入出力の層が
 * {@code switch} で場合を尽くして応答か業務エラーに変える。
 */
public sealed interface ReferenceRowsResult {

    /**
     * 読めた（200）。
     *
     * @param rows 行
     */
    record Found(ReferenceRows rows) implements ReferenceRowsResult {

        /** 行が必須であることを確かめる。 */
        public Found {
            Objects.requireNonNull(rows, "rows は必須です");
        }
    }

    /** 名前・権限・DSL・型として読めないラベルの値・行が無いラベル（403 の同じ応答。BR1.3・BR6.2・BR6.4）。 */
    record Denied() implements ReferenceRowsResult {}

    /**
     * 検索の {@code EQUALS} の値を型として読めない（400 {@code VALIDATION_FAILED} の {@code INVALID_VALUE}。BR5.6）。
     *
     * @param column 条件のカラム（ピッカーの {@code searchFields} で利用者に見せたもの）
     */
    record InvalidValue(String column) implements ReferenceRowsResult {

        /** カラムが必須であることを確かめる。 */
        public InvalidValue {
            Objects.requireNonNull(column, "column は必須です");
        }
    }

    /**
     * 対象DB の失敗（BR1.5）。
     *
     * @param reason 理由
     */
    record Failed(ReferenceFailureReason reason) implements ReferenceRowsResult {

        /** 理由が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(reason, "reason は必須です");
        }
    }
}
