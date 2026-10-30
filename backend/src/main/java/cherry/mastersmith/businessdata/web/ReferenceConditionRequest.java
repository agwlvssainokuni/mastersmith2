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

/**
 * 参照先の検索 API の本文の条件1つ（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の {@code conditions[]}）。文字列にするときは
 * カラムの名前と値の数だけを出す（BR10.1）。
 *
 * @param column 条件のカラムの名前（参照先の {@code searchFields} のもの）
 * @param value 値
 */
public record ReferenceConditionRequest(String column, String value) {

    /**
     * 確かめる前の条件にする。
     *
     * @return 条件
     */
    RawCondition toRaw() {
        return new RawCondition(column, value, null, null, null);
    }

    /** カラムの名前と値の数だけを出す。 */
    @Override
    public String toString() {
        return toRaw().toString();
    }
}
