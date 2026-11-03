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
package cherry.mastersmith.businessdata.repository;

/** 結果の1つの値を文字にした結果（BR7.2・BR7.6、NFR 設計のレビュー R-02）。 */
public sealed interface CellValue {

    /**
     * 文字にできた。
     *
     * @param text 文字（NULL は null）
     * @param truncated 上限の文字数で切ったなら true
     */
    record Text(String text, boolean truncated) implements CellValue {

        /** 値を出さない。 */
        @Override
        public String toString() {
            return "Text[value=" + (text == null ? "null" : "***") + ", truncated=" + truncated + "]";
        }
    }

    /**
     * 日時・時刻の型として読めなかったが、DB が送った文字を取り出せた（ゼロの日時・無限大など。そのセルだけ DB の文字のまま返す。BR3.10）。
     *
     * @param text DB が送った文字
     */
    record Raw(String text) implements CellValue {

        /** 値を出さない。 */
        @Override
        public String toString() {
            return "Raw[value=***]";
        }
    }

    /**
     * DSL の型で読めなかった（ゼロ日付・範囲の外の値など。D1: A の 409 {@code BUSINESS_TARGET_DB_MISMATCH}）。
     *
     * @param exceptionClass 例外のクラスの名前（メッセージは持たない）
     */
    record Unreadable(String exceptionClass) implements CellValue {}
}
