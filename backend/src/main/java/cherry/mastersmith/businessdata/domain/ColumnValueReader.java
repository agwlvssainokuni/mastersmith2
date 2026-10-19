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

import java.util.Optional;

/**
 * 要求の文字をカラムの型として読む口（BR4.7・BR6.2・BR7.8）。実装は {@code repository} の {@code ColumnValueCodec} の1か所で、
 * 読めない値（桁あふれを含む）は例外にせず空を返す。
 */
@FunctionalInterface
public interface ColumnValueReader {

    /**
     * 文字をカラムの型として読む。
     *
     * @param column カラム
     * @param text 要求の文字（null でない）
     * @return 型として読んだ値（読めなければ空）
     */
    Optional<BoundValue> read(ColumnAccess column, String text);
}
