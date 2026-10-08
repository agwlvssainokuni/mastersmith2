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
 * グループの操作の業務の拒否の理由（BR1.4・BR1.5・BR2.3・BR3.1・BR3.3・BR3.4・BR4.1）。1つの理由に1つの code（BR10.1）と1つの監査の
 * 失敗の理由（BR8.2）が当たる。排他の待ちの上限切れ（{@code GROUP_BUSY}）は業務の拒否ではないため、ここに置かない（BR5.2・BR8.3）。
 */
public enum GroupRejection {
    /** 操作の対象のグループがいない（404）。 */
    GROUP_NOT_FOUND(GroupAuditFailure.GROUP_NOT_FOUND),
    /** メンバーに足す利用者がいない（404）。 */
    USER_NOT_FOUND(GroupAuditFailure.USER_NOT_FOUND),
    /** 名前がほかのグループと重なる（409）。 */
    NAME_DUPLICATE(GroupAuditFailure.GROUP_NAME_DUPLICATE),
    /** メンバーかロールの割り当てが残る（409）。 */
    IN_USE(GroupAuditFailure.GROUP_IN_USE),
    /** 変えるものが無い（409）。 */
    NO_CHANGE(GroupAuditFailure.NO_CHANGE);

    private final GroupAuditFailure auditFailure;

    GroupRejection(GroupAuditFailure auditFailure) {
        this.auditFailure = auditFailure;
    }

    /**
     * 監査に残す失敗の理由を返す（BR8.2）。
     *
     * @return 監査の失敗の理由
     */
    public GroupAuditFailure auditFailure() {
        return auditFailure;
    }
}
