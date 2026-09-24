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

/** 指標とログの結果（{@code mastersmith.dsl.operation} のタグ {@code outcome}。NFR1.16）。値は決まった語だけ。 */
public enum DslOutcome {
    /** 成功（照合では、対象DB を読めて照合した）。 */
    SUCCESS("success"),
    /** 想定内の拒否（検証・大きさ・プレビューの不一致・プレビューが無い）。 */
    REJECTED("rejected"),
    /** 失敗（対象DB の設定が無い・接続できない、想定外の失敗）。 */
    FAILED("failed"),
    /** 重い処理の許可が取れず、断った。 */
    BUSY("busy");

    private final String tag;

    DslOutcome(String tag) {
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
