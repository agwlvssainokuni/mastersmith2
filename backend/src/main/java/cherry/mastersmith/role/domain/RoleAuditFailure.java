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

/**
 * ロールの操作の監査の失敗の理由（BR11.2、契約 C10）。{@code audit} が監査の失敗の理由に写す（{@code NO_CHANGE} は既存の値、ほかは
 * 足した値）。B4 と B5 の分。{@code ROLE_TRANSFER_STALE}（B6）はその Bolt で足す（{@code USER_NOT_FOUND}・
 * {@code GROUP_NOT_FOUND} は既存の値に写す）。
 */
public enum RoleAuditFailure {
    /** 操作の対象のロールがいない。 */
    ROLE_NOT_FOUND,
    /** ロールの名前がほかのロールと重なる。 */
    ROLE_NAME_DUPLICATE,
    /** 割り当てが残るロールの削除。 */
    ROLE_IN_USE,
    /** 今の DSL に無い対象への値の設定。 */
    PERMISSION_TARGET_NOT_IN_DSL,
    /** 適用済みの DSL が無いときの保存。 */
    DSL_NOT_APPLIED,
    /** 変えるものが無い（同じ名前への変更・変わる対象の無い保存・消す行の無い消す操作・重ねての割り当て・割り当てていない組の外し）。 */
    NO_CHANGE,
    /** 割り当ての外・存在しないロールへの作業ロールの切り替え（B5、BR7.5）。 */
    ROLE_NOT_ASSIGNED,
    /** 割り当ての相手の利用者がいない（招待中の人を含む。B5、BR6.1）。 */
    USER_NOT_FOUND,
    /** 割り当ての相手のグループがいない（B5、BR6.6）。 */
    GROUP_NOT_FOUND
}
