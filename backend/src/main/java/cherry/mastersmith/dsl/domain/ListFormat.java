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

/** 一覧の表示の書式の種類（DSL の {@code list.format}。書式の中身は画面の Intent で決める）。 */
public enum ListFormat {
    /** 日付。 */
    DATE,
    /** 日時。 */
    DATETIME,
    /** 時刻。 */
    TIME,
    /** 数値の桁区切り。 */
    NUMBER_GROUPED,
    /** 真偽値を「はい・いいえ」で示す。 */
    BOOLEAN_YES_NO,
    /** 真偽値を「オン・オフ」で示す。 */
    BOOLEAN_ON_OFF
}
