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
package cherry.mastersmith.dslmanage.domain;

/** DSL の操作の出来事の種類（契約 C7 の {@code type}）。 */
public enum DslOperationType {
    /** スキーマの読み込みで既定の DSL をプレビューに置いた。 */
    DSL_GENERATED,
    /** DSL を投入した（アップロード・貼り付け・履歴からの戻し）。 */
    DSL_SUBMITTED,
    /** 投入を受け付けなかった（大きさ・検証）。 */
    DSL_SUBMISSION_REJECTED,
    /** プレビューを適用した。 */
    DSL_APPLIED,
    /** プレビューを破棄した。 */
    DSL_PREVIEW_DISCARDED
}
