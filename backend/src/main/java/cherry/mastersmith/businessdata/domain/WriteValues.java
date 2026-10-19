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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 画面が送ったカラムと値の組（確かめる前。Intent 261016-business-data-views の U2、entities.md の WriteValues、BR12.1）。値は文字か
 * null で、null と空の文字列を見分ける。
 *
 * <p>文字列にするときはカラムの数だけを出す（名前と値を出さない。{@code TraceAspect} が service の層の引数を TRACE に出すため）。
 *
 * @param values カラムの名前と値（送った順。値は null を許す。変更できない）
 */
public record WriteValues(Map<String, String> values) {

    /** 対応表を、順を保ったまま変更できないものにする。 */
    public WriteValues {
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** カラムの数だけを出す。 */
    @Override
    public String toString() {
        return "WriteValues[columns=" + values.size() + "]";
    }
}
