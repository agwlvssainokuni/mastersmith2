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
package cherry.mastersmith.group.domain;

/** グループの操作の区分（監査の種類と、待ち合わせの口に使う。{@code entities.md} の GroupOperation）。 */
public enum GroupOperation {
    /** グループの作成。 */
    CREATE,
    /** グループの名前の変更。 */
    RENAME,
    /** グループの削除。 */
    DELETE,
    /** メンバーの追加。 */
    ADD_MEMBER,
    /** メンバーの外し。 */
    REMOVE_MEMBER
}
