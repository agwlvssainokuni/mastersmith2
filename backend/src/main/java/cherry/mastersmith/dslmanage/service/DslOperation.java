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
package cherry.mastersmith.dslmanage.service;

/** 指標とログの操作の種類（{@code mastersmith.dsl.operation} のタグ {@code operation}。NFR1.16）。値は決まった語だけ。 */
public enum DslOperation {
    /** スキーマの読み込み（既定の DSL の生成）。 */
    GENERATE("generate"),
    /** 投入（アップロード・貼り付け）。 */
    SUBMIT("submit"),
    /** 履歴からの戻し。 */
    RESTORE("restore"),
    /** 適用。 */
    APPLY("apply"),
    /** 破棄。 */
    DISCARD("discard"),
    /** プレビューの表示の中の照合。 */
    COMPARE("compare");

    private final String tag;

    DslOperation(String tag) {
        this.tag = tag;
    }

    /**
     * タグの値を返す。
     *
     * @return タグの値
     */
    public String tag() {
        return tag;
    }
}
