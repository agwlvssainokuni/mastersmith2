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

/**
 * グループの操作の監査の失敗の理由（BR8.2、契約 C10）。{@code audit} が監査の失敗の理由に写す（{@code USER_NOT_FOUND}・
 * {@code NO_CHANGE} は既存の値、ほかは足した値）。
 */
public enum GroupAuditFailure {
    /** 操作の対象のグループがいない。 */
    GROUP_NOT_FOUND,
    /** メンバーに足す利用者がいない（招待中で登録が終わっていない人を含む）。 */
    USER_NOT_FOUND,
    /** グループの名前がほかのグループと重なる。 */
    GROUP_NAME_DUPLICATE,
    /** メンバーかロールの割り当てが残るグループの削除。 */
    GROUP_IN_USE,
    /** 変えるものが無い（同じ名前への変更・重ねての追加・メンバーでない人の外し）。 */
    NO_CHANGE
}
