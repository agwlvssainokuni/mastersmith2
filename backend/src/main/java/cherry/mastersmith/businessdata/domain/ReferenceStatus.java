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

/**
 * 参照の項目の状態（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の {@code reference.status}、entities.md の
 * ReferenceStatus）。{@link ReferencePolicy#accessOf} は {@link #AVAILABLE}・{@link #NO_PERMISSION} だけを返し、{@link #TOO_MANY}
 * は REFERENCE で 201 行目を読んだときに重ねる（BR3.1・BR4.3）。
 */
public enum ReferenceStatus {
    /** 参照先を読める（選択肢・ピッカーの情報を返す）。 */
    AVAILABLE,
    /** 参照先を読めない、または参照元のカラムが用途で入力できない（BR3.1・BR3.3）。 */
    NO_PERMISSION,
    /** REFERENCE の候補が 200 を超える（BR4.3。LOOKUP には無い）。 */
    TOO_MANY
}
