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

import java.util.Objects;
import java.util.Optional;

/**
 * 5つの操作の拒否の判定（BR2.1・BR2.2、FS の 2.2 の擬似コード）。DB にも時計にも触れない純粋な関数である。
 *
 * <p>操作ごとに当たりうる理由（{@link #applies}）だけを {@link RejectionReason} の宣言の順に調べ、最初に当たった理由を1つ返す。
 * どれにも当たらなければ空（拒否しない）を返す。
 *
 * <ul>
 *   <li>印を付ける: 対象がいない・自分自身・対象が停止中・変えるものが無い（すでに管理者）
 *   <li>印を外す: 対象がいない・自分自身・対象が停止中・変えるものが無い（管理者でない）・最後の有効な管理者
 *   <li>止める: 対象がいない・自分自身・変えるものが無い（すでに停止中）・最後の有効な管理者（対象が停止中は当てない）
 *   <li>停止を解く: 対象がいない・自分自身・変えるものが無い（停止中でない）
 *   <li>失敗回数を戻す: 対象がいない・変えるものが無い（戻せない）（自分自身と対象が停止中は当てない）
 * </ul>
 */
public final class RejectionPolicy {

    private RejectionPolicy() {}

    /**
     * 拒否の理由を決める。
     *
     * @param operation 操作の区分
     * @param facts 事実の組
     * @return 最初に当たった理由（拒否しなければ空）
     */
    public static Optional<RejectionReason> decide(AdminOperation operation, OperationFacts facts) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(facts, "facts");
        for (RejectionReason reason : RejectionReason.values()) {
            if (applies(operation, reason) && holds(operation, reason, facts)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }

    /**
     * 操作に当たりうる理由かを返す（BR2.2）。
     *
     * @param operation 操作の区分
     * @param reason 理由
     * @return 当たりうるなら真
     */
    public static boolean applies(AdminOperation operation, RejectionReason reason) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(reason, "reason");
        return switch (reason) {
            case USER_NOT_FOUND, NO_CHANGE -> true;
            case SELF_OPERATION -> operation != AdminOperation.RESET_LOGIN_FAILURES;
            case TARGET_SUSPENDED ->
                operation == AdminOperation.GRANT_ADMIN || operation == AdminOperation.REVOKE_ADMIN;
            case LAST_ACTIVE_ADMIN -> operation == AdminOperation.REVOKE_ADMIN || operation == AdminOperation.SUSPEND;
        };
    }

    /** 理由の条件が事実の組で成り立つかを返す（当たりうるかは見ない）。 */
    private static boolean holds(AdminOperation operation, RejectionReason reason, OperationFacts facts) {
        return switch (reason) {
            case USER_NOT_FOUND -> !facts.targetExists();
            case SELF_OPERATION -> facts.targetIsOperator();
            case TARGET_SUSPENDED -> facts.targetSuspended();
            case NO_CHANGE -> noChange(operation, facts);
            case LAST_ACTIVE_ADMIN -> facts.leavesNoActiveAdmin();
        };
    }

    /** 変えるものが無いかを返す（すでにそうなっている・戻せない）。 */
    private static boolean noChange(AdminOperation operation, OperationFacts facts) {
        return switch (operation) {
            case GRANT_ADMIN -> facts.targetAdmin();
            case REVOKE_ADMIN -> !facts.targetAdmin();
            case SUSPEND -> facts.targetSuspended();
            case RESUME -> !facts.targetSuspended();
            case RESET_LOGIN_FAILURES -> !facts.targetResettable();
        };
    }
}
