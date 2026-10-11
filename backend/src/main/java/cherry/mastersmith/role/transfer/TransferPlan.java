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
package cherry.mastersmith.role.transfer;

import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.TransferAction;
import java.util.List;
import java.util.Objects;

/**
 * 権限の YAML の確かめ・適用で作る変わる点の一覧と指紋（BR9.6〜BR9.9、{@code entities.md} の TransferPlan）。サーバーは保存しない
 * （BR9.15）。
 *
 * <p>{@link #toString()} はロールの数と変わる対象の数だけを出す（NFR1.9）。
 *
 * @param fingerprint 指紋（SHA-256 の 16 進数の小文字 64 文字。BR9.9）
 * @param roles ロールごとの変わる点（ロールの鍵のコードポイントの順）
 * @param untouchedRoles ファイルに無い今のロールの名前（ロールの ID の順）
 */
public record TransferPlan(String fingerprint, List<TransferRolePlan> roles, List<String> untouchedRoles) {

    /** 写して持ち、指紋の形を確かめる。 */
    public TransferPlan {
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (!TransferFingerprint.isWellFormed(fingerprint)) {
            throw new IllegalArgumentException("指紋は 16 進数の小文字 64 文字です");
        }
        roles = List.copyOf(roles);
        untouchedRoles = List.copyOf(untouchedRoles);
    }

    /**
     * 変わる点が1つも無いか（作るロールも、変わる対象も無い。BR9.12）。
     *
     * @return 変わる点が無ければ真
     */
    public boolean isEmpty() {
        return roles.stream()
                .allMatch(role -> role.action() == TransferAction.REPLACE
                        && role.changes().isEmpty());
    }

    /**
     * 置き換えるロールの ID を昇順で返す（適用の排他の順。BR9.10）。
     *
     * @return ロールの ID（昇順）
     */
    public List<Long> replacedRoleIds() {
        return roles.stream()
                .map(TransferRolePlan::roleId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    /**
     * 変わる対象の数の合計を返す。
     *
     * @return 数
     */
    public int changeCount() {
        return roles.stream().mapToInt(role -> role.changes().size()).sum();
    }

    /**
     * 監査の detail にする（BR11.7）。
     *
     * @param fileSha256 読み込んだファイルの SHA-256
     * @param fingerprint 監査に残す指紋（成功は一致した指紋、拒否は要求の指紋）
     * @return detail
     */
    public RoleAuditDetail.Transfer toAuditDetail(String fileSha256, String fingerprint) {
        return new RoleAuditDetail.Transfer(
                fileSha256,
                fingerprint,
                roles.stream().map(TransferRolePlan::toAuditRole).toList());
    }

    /** ロールの数と変わる対象の数だけを出す。 */
    @Override
    public String toString() {
        return "TransferPlan[roles=" + roles.size() + ", changes=" + changeCount() + ", untouched="
                + untouchedRoles.size() + "]";
    }
}
