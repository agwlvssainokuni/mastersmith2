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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.RawCondition;
import java.util.List;

/**
 * 一覧と検索の本文の条件1つ（契約 C2 の {@code conditions[]}）。文字列にするときはカラムの名前と値の数だけを出す（BR10.1）。
 *
 * @param column カラムの名前
 * @param value EQUALS・CONTAINS の値
 * @param from RANGE の下限
 * @param to RANGE の上限
 * @param values CHOICE の値
 */
public record SearchConditionRequest(String column, String value, String from, String to, List<String> values) {

    /**
     * 確かめる前の条件にする。
     *
     * @return 条件
     */
    RawCondition toRaw() {
        return new RawCondition(column, value, from, to, values);
    }

    /** カラムの名前と値の数だけを出す。 */
    @Override
    public String toString() {
        return toRaw().toString();
    }
}
