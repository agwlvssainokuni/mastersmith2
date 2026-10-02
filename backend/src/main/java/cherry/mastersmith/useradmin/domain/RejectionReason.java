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
package cherry.mastersmith.useradmin.domain;

/**
 * 業務の拒否の理由（BR2.1〜BR2.3）。宣言の並びが判定の順で、最初に当たった理由1つで拒否する。応答の code（{@link
 * UserAdminProblemTypes}）と監査の理由（{@link UserAdminAuditFailure}）に1対1で写す。
 *
 * <p>排他の待ちの上限切れ（Busy）と操作した人の確かめ直し（NOT_ADMIN）は業務の理由の外にあり、ここには置かない。
 */
public enum RejectionReason {
    /** 対象の利用者がいない（404 USER_NOT_FOUND）。 */
    USER_NOT_FOUND,
    /** 自分自身への操作（409 USER_ADMIN_SELF_OPERATION）。 */
    SELF_OPERATION,
    /** 対象が停止中（409 USER_ADMIN_TARGET_SUSPENDED）。 */
    TARGET_SUSPENDED,
    /** 変えるものが無い（409 USER_ADMIN_NO_CHANGE）。 */
    NO_CHANGE,
    /** 対象を除くと有効な管理者が 0 人になる（409 USER_ADMIN_LAST_ADMIN）。 */
    LAST_ACTIVE_ADMIN
}
