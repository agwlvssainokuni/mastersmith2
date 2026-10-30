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
 * 作成の必須の参照項目（参照のカラムで、作成で入力でき、NULL を許さないもの）の状態（Intent 261029-reference-lookup-cleanup の U2、
 * entities.md の RequiredReferenceState、BR3.1〜BR3.5）。値を入れられるか（入力不可の行き止まりか）を表す。値を持たない。
 *
 * @param column 参照元のカラムの名前
 * @param status 参照先の状態（REFERENCE は {@code countState}、LOOKUP は {@code accessOf} で決めたもの）
 * @param bodyState 本文の状態（{@code ⑨} の既存の検証の結果で決める。状態によって変わらない）
 */
public record RequiredReferenceState(String column, ReferenceStatus status, BodyState bodyState) {

    /** 本文の状態（BR2.3・BR3.1）。 */
    public enum BodyState {
        /** 検証を通った値がある（文字の型で REQUIRED が無いカラムの空文字列を含む）。 */
        VALUE,
        /** 本文に無い・null、または空文字列で既存の検証が誤りにしたもの。 */
        EMPTY
    }

    /** 必須の値を確かめる。 */
    public RequiredReferenceState {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(status, "status は必須です");
        Objects.requireNonNull(bodyState, "bodyState は必須です");
    }
}
