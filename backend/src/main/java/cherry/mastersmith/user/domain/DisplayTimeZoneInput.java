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
package cherry.mastersmith.user.domain;

/**
 * 表示のタイムゾーンの保存の入力の 3 状態（Intent 261102-timezone-ui-polish の U2、BR2.3・BR2.4）。検証の前の値を運ぶ。
 *
 * <ul>
 *   <li>{@link Keep}: 本文に項目が無い（前の版の画面と互換。列を書き換えない）
 *   <li>{@link Clear}: 明示の {@code null}（列を空にする。「ブラウザに合わせる」）
 *   <li>{@link Text}: 文字列（名前の門で照らす）
 *   <li>{@link NotText}: 文字列でも {@code null} でもない型（数・真偽値・オブジェクト・配列。必ず拒否する）
 * </ul>
 *
 * <p>{@link Text}・{@link NotText} は検証の前の値を持つため、文字列化で中身を伏せる（SD2）。
 */
public sealed interface DisplayTimeZoneInput {

    /** 項目が無い（列を書き換えない）。 */
    record Keep() implements DisplayTimeZoneInput {}

    /** 明示の null（列を空にする）。 */
    record Clear() implements DisplayTimeZoneInput {}

    /**
     * 文字列。
     *
     * @param value 検証の前の値
     */
    record Text(String value) implements DisplayTimeZoneInput {

        /** 値を伏せて文字列にする。 */
        @Override
        public String toString() {
            return "Text[value=***]";
        }
    }

    /**
     * 文字列でも null でもない型（値は持たない）。
     *
     * @param kind 型の名前（値は載せない。例: {@code NUMBER}）
     */
    record NotText(String kind) implements DisplayTimeZoneInput {

        /** 型の名前だけを文字列にする。 */
        @Override
        public String toString() {
            return "NotText[kind=" + kind + "]";
        }
    }
}
