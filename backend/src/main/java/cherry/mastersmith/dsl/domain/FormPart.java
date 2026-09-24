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
package cherry.mastersmith.dsl.domain;

/** カラムのフォーム部品（DSL の {@code formPart}。DSL には小文字の名前で書く）。 */
public enum FormPart {
    TEXT("text"),
    TEXTAREA("textarea"),
    CHECKBOX("checkbox"),
    SWITCH("switch"),
    RADIO("radio"),
    SELECT("select"),
    DATE("date"),
    DATETIME("datetime"),
    NUMBER("number"),
    PASSWORD("password"),
    LOOKUP("lookup");

    private final String dslName;

    FormPart(String dslName) {
        this.dslName = dslName;
    }

    /**
     * DSL に書く名前を返す。
     *
     * @return DSL に書く名前
     */
    public String dslName() {
        return dslName;
    }

    /**
     * DSL に書いた名前から引く。
     *
     * @param dslName DSL に書いた名前
     * @return フォーム部品
     * @throws IllegalArgumentException 知らない名前のとき
     */
    public static FormPart fromDslName(String dslName) {
        for (FormPart part : values()) {
            if (part.dslName.equals(dslName)) {
                return part;
            }
        }
        throw new IllegalArgumentException("知らないフォーム部品です");
    }
}
