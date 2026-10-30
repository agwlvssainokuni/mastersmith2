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

import java.util.Objects;

/**
 * 保存前の確かめの口 {@code check} の結果（Intent 261029-reference-lookup-cleanup の U1、契約 C1 を機能設計の D3・D8 で直した形、
 * entities.md の ReferenceCheckResult、BR9.1〜BR9.5）。U2 が保存の前に受け、{@code NotFoundOrDenied} を {@code REFERENCE_NOT_FOUND}
 * に、{@code Failed} を理由ごとに既存の code に写す。値・ラベルを持たない。
 */
public sealed interface ReferenceCheckResult {

    /** 参照先に、型つきの値として厳密に一致する行があり、読める（BR9.4）。 */
    record Found() implements ReferenceCheckResult {}

    /** 参照先に無い・読めない・型として読めない値（区別しない。BR9.2〜BR9.4）。 */
    record NotFoundOrDenied() implements ReferenceCheckResult {}

    /**
     * 対象DB の失敗（BR9.5）。理由は {@code failure().reason()} で読む（reliability-design.md 5.2）。
     *
     * @param failure メッセージを捨てた失敗の値
     */
    record Failed(ReferenceReadFailure failure) implements ReferenceCheckResult {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
