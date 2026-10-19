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

import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import java.util.Objects;

/** 詳細の API の結果（functional-spec.md の 3.3節）。 */
public sealed interface RowReadResult permits RowReadResult.Found, ReadRejected {

    /**
     * 1行を返す。
     *
     * @param view 判定の材料
     * @param row 行（主キーと詳細の列の値）
     * @param definitionChanged 要求の定義の識別が今と違うなら true（BR9.1）
     */
    record Found(TableAccessView view, RowRecord row, boolean definitionChanged) implements RowReadResult {

        /** 必須の値を確かめる。 */
        public Found {
            Objects.requireNonNull(view, "view は必須です");
            Objects.requireNonNull(row, "row は必須です");
        }
    }
}
