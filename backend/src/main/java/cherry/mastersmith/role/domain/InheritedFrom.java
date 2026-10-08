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
package cherry.mastersmith.role.domain;

/** 実効の値の出どころ（{@code entities.md} の InheritedFrom、契約 C7 の {@code inheritedFrom}、BR4.11）。 */
public enum InheritedFrom {
    /** その対象の明示の値。 */
    EXPLICIT,
    /** テーブルの明示の値から継承。 */
    TABLE,
    /** スキーマの明示の値から継承。 */
    SCHEMA,
    /** どの階層も設定なし（主権限は NONE、補助権限は不可）。 */
    DEFAULT
}
