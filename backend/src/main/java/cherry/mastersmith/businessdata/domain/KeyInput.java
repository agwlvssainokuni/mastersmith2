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
 * 詳細の本文の主キー（確かめる前。契約 C2 の {@code key}、BR6.2）。カラムごとに分けて受け、区切りの文字で連結しない。
 *
 * <p>文字列にするときはカラムの数だけを出す（BR10.1）。
 *
 * @param values カラムの名前と値（照らす前。値は null を許す）
 */
public record KeyInput(Map<String, String> values) {

    /** 対応表を、順を保ったまま変更できないものにする。 */
    public KeyInput {
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** カラムの数だけを出す（名前と値を出さない）。 */
    @Override
    public String toString() {
        return "KeyInput[columns=" + values.size() + "]";
    }
}
