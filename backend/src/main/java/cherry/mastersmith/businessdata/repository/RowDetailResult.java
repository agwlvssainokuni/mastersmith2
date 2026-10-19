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

import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowRecord;
import java.util.Objects;

/** 詳細の読み取りの結果（1行・0行・2行以上・失敗。BR6.3）。 */
public sealed interface RowDetailResult {

    /**
     * 1行が当たった。
     *
     * @param row 行
     */
    record Found(RowRecord row) implements RowDetailResult {

        /** 行が必須であることを確かめる。 */
        public Found {
            Objects.requireNonNull(row, "row は必須です");
        }
    }

    /** 当たる行が無い。 */
    record NotFound() implements RowDetailResult {}

    /** 2行以上が当たった。 */
    record NotUnique() implements RowDetailResult {}

    /**
     * 失敗した。
     *
     * @param failure 失敗
     */
    record Failed(RowQueryFailure failure) implements RowDetailResult {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
