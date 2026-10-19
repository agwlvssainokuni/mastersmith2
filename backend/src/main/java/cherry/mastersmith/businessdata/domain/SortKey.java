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
package cherry.mastersmith.businessdata.domain;

import cherry.mastersmith.dsl.domain.SortDirection;
import java.util.Objects;

/**
 * 並べ替えのカラムと向き（entities.md の SortKey）。
 *
 * @param column カラムの名前（照らした後の名前）
 * @param direction 向き
 */
public record SortKey(String column, SortDirection direction) {

    /** 必須の値を確かめる。 */
    public SortKey {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(direction, "direction は必須です");
    }
}
