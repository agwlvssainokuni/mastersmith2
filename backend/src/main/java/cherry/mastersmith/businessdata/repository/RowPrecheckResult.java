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

import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import java.util.List;
import java.util.Objects;

/** 一意の事前の問い合わせ（と違反の後の一意の確かめ）の結果（Intent 261016-business-data-views の U2、BR5.1・BR6.2）。 */
public sealed interface RowPrecheckResult {

    /** 重なる行は無い（問い合わせるカラムが無いときを含む）。 */
    record Clear() implements RowPrecheckResult {}

    /**
     * 同じ値のほかの行がある。
     *
     * @param columns 重なったカラム（送った順）
     */
    record Duplicate(List<String> columns) implements RowPrecheckResult {

        /** 一覧を変更できないものにし、1つ以上であることを確かめる。 */
        public Duplicate {
            columns = List.copyOf(columns);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("重なったカラムは1つ以上です");
            }
        }
    }

    /**
     * 失敗した。
     *
     * @param failure 失敗
     */
    record Failed(RowWriteFailure failure) implements RowPrecheckResult {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
