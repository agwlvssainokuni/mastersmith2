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
package cherry.mastersmith.businessdata.repository;

import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import java.util.Objects;

/** 作成の文の結果（Intent 261016-business-data-views の U2、BR7.2）。 */
public sealed interface RowInsertResult {

    /**
     * 1行を作った。
     *
     * @param key 作った行の主キーの文字の値（主キーの順。主キーの無いテーブル・生成された鍵を読めないときは空。文字列にするときは値を
     *     伏せる）
     */
    record Inserted(RowValues key) implements RowInsertResult {

        /** 主キーが必須であることを確かめる。 */
        public Inserted {
            Objects.requireNonNull(key, "key は必須です");
        }
    }

    /**
     * 失敗した。
     *
     * @param failure 失敗
     */
    record Failed(RowWriteFailure failure) implements RowInsertResult {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
