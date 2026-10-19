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
import java.util.Objects;
import java.util.Optional;

/** 違反の後の確かめの結果（Intent 261016-business-data-views の U2、BR6.2・BR6.4）。 */
public sealed interface RowInspectResult {

    /**
     * 確かめた。
     *
     * @param column 違反のカラム（候補がちょうど1つのときだけ。0 か 2 以上なら空）
     */
    record Inspected(Optional<String> column) implements RowInspectResult {

        /** 必須の値を確かめる。 */
        public Inspected {
            Objects.requireNonNull(column, "column は必須です");
        }
    }

    /**
     * 確かめの問い合わせが失敗した（BR6.4）。
     *
     * @param failure 失敗
     */
    record Failed(RowWriteFailure failure) implements RowInspectResult {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
