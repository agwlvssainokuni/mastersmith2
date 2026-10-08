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
 * ロールの操作の業務の拒否の理由（BR1.4・BR1.5・BR3.2・BR4.5〜BR4.8・BR6.1・BR6.3・BR6.4・BR6.6・BR7.5）。1つの理由に1つの code（BR13.1）と1つの監査の失敗の理由
 * （BR11.2）が当たる。排他の待ちの上限切れ（{@code ROLE_BUSY}）は業務の拒否ではないため、ここに置かない（BR8.3）。
 */
public enum RoleRejection {
    /** 操作の対象のロールがいない（404）。 */
    ROLE_NOT_FOUND(RoleAuditFailure.ROLE_NOT_FOUND),
    /** 名前がほかのロールと重なる（409）。 */
    NAME_DUPLICATE(RoleAuditFailure.ROLE_NAME_DUPLICATE),
    /** 割り当てが残る（409）。 */
    IN_USE(RoleAuditFailure.ROLE_IN_USE),
    /** 変えるものが無い（409）。 */
    NO_CHANGE(RoleAuditFailure.NO_CHANGE),
    /** 今の DSL に無い対象への値の設定（409）。 */
    TARGET_NOT_IN_DSL(RoleAuditFailure.PERMISSION_TARGET_NOT_IN_DSL),
    /** 適用済みの DSL が無い（409）。 */
    DSL_NOT_APPLIED(RoleAuditFailure.DSL_NOT_APPLIED),
    /** 割り当ての外・存在しないロールへの作業ロールの切り替え（409。B5、BR7.5）。 */
    NOT_ASSIGNED(RoleAuditFailure.ROLE_NOT_ASSIGNED),
    /** 割り当ての相手の利用者がいない（404。user の {@code USER_NOT_FOUND}。B5、BR6.1）。 */
    USER_NOT_FOUND(RoleAuditFailure.USER_NOT_FOUND),
    /** 割り当ての相手のグループがいない（404。group の {@code GROUP_NOT_FOUND}。B5、BR6.6）。 */
    GROUP_NOT_FOUND(RoleAuditFailure.GROUP_NOT_FOUND);

    private final RoleAuditFailure auditFailure;

    RoleRejection(RoleAuditFailure auditFailure) {
        this.auditFailure = auditFailure;
    }

    /**
     * 監査に残す失敗の理由を返す（BR11.2）。
     *
     * @return 監査の失敗の理由
     */
    public RoleAuditFailure auditFailure() {
        return auditFailure;
    }
}
