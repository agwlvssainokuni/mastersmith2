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
 * U2 に向けた2つ目の口 {@code countState} の結果（Intent 261029-reference-lookup-cleanup の U1、機能設計の D7、entities.md の
 * ReferenceCountState、BR9.7）。値・ラベル・件数そのものを持たない。
 */
public sealed interface ReferenceCountState {

    /** 参照先を読め、候補は 200 以下（LOOKUP は数えず常にこれ）。 */
    record Available() implements ReferenceCountState {}

    /** REFERENCE の候補が 201 以上（BR4.3 と同じ境界）。 */
    record TooMany() implements ReferenceCountState {}

    /** 参照でないカラム、または参照先を読めない（BR3.1）。 */
    record NoPermission() implements ReferenceCountState {}

    /**
     * 対象DB の失敗（BR9.5 と同じ）。理由は {@code failure().reason()} で読む。
     *
     * @param failure メッセージを捨てた失敗の値
     */
    record Failed(ReferenceReadFailure failure) implements ReferenceCountState {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
