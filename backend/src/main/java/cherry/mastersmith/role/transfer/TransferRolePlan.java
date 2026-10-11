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
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.TransferAction;
import java.util.List;
import java.util.Objects;

/**
 * 権限の YAML の読み込みの1つのロールの変わる点（BR9.6〜BR9.8、{@code entities.md} の TransferRolePlan）。
 *
 * <p>{@link #toString()} は作るか置き換えるかと数だけを出し、名前を出さない（NFR1.9）。
 *
 * @param name ロールの名前（作るときはファイルの名前、置き換えるときは今の名前。BR9.6 で名前は変えない）
 * @param fileName ファイルの中の名前（作るときに使う。名前の規則で正規化した値）
 * @param roleId 置き換えるロールの ID（作るときは null）
 * @param action 作るか置き換えるか
 * @param changes 変わる対象（対象の順）
 */
public record TransferRolePlan(
        String name, RoleName fileName, Long roleId, TransferAction action, List<TransferChange> changes) {

    /** 写して持ち、置き換えのときだけ ID を持つことを確かめる。 */
    public TransferRolePlan {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(action, "action");
        changes = List.copyOf(changes);
        if ((action == TransferAction.REPLACE) != (roleId != null)) {
            throw new IllegalArgumentException("置き換えのときだけロールの ID を持ちます");
        }
    }

    /**
     * 変わり方ごとの数を返す。
     *
     * @param reason 変わり方
     * @return 数
     */
    public int count(TransferChange.Reason reason) {
        return (int)
                changes.stream().filter(change -> change.reason() == reason).count();
    }

    /**
     * 変わる対象のうち今の DSL に無いものの数を返す（BR9.8）。
     *
     * @return 数
     */
    public int notInDsl() {
        return (int) changes.stream().filter(change -> !change.inCurrentDsl()).count();
    }

    /**
     * 監査の detail のロールの要約にする（BR11.7）。
     *
     * @return 要約
     */
    public RoleAuditDetail.TransferRole toAuditRole() {
        return new RoleAuditDetail.TransferRole(
                name,
                roleId,
                action,
                count(TransferChange.Reason.ADDED),
                count(TransferChange.Reason.CHANGED),
                count(TransferChange.Reason.CLEARED),
                notInDsl());
    }

    /** 作るか置き換えるかと数だけを出す。 */
    @Override
    public String toString() {
        return "TransferRolePlan[" + action + ", changes=" + changes.size() + "]";
    }
}
