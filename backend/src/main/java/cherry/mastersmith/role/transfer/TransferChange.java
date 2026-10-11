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

import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import java.util.Objects;

/**
 * 権限の YAML の読み込みで変わる1つの対象（BR9.7・BR9.8、{@code entities.md} の TransferChange）。
 *
 * <p>{@link #toString()} は理由と階層だけを出し、対象の名前と値を出さない（NFR1.9）。
 *
 * @param target 対象
 * @param before 前の値（行が無ければすべて設定なし）
 * @param after 後の値（消えるならすべて設定なし）
 * @param reason 変わり方
 * @param inCurrentDsl 今の DSL にある対象か
 */
public record TransferChange(
        PermissionTarget target, PermissionValues before, PermissionValues after, Reason reason, boolean inCurrentDsl) {

    /** 変わり方（{@code entities.md} の TransferChange の reason）。 */
    public enum Reason {
        /** 今は設定が無く、ファイルにある対象。 */
        ADDED,
        /** 今の設定とファイルの値が違う対象。 */
        CHANGED,
        /** 今は設定があり、ファイルに無い対象（設定なしになる）。 */
        CLEARED
    }

    /** 値が null でなく、前後の値と変わり方が合うことを確かめる。 */
    public TransferChange {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(reason, "reason");
        boolean consistent =
                switch (reason) {
                    case ADDED -> before.isEmpty() && !after.isEmpty();
                    case CHANGED -> !before.isEmpty() && !after.isEmpty() && !before.equals(after);
                    case CLEARED -> !before.isEmpty() && after.isEmpty();
                };
        if (!consistent) {
            throw new IllegalArgumentException("前後の値と変わり方が合いません: " + reason);
        }
    }

    /** 理由と階層だけを出す。 */
    @Override
    public String toString() {
        return "TransferChange[" + reason + ", " + target.level() + "]";
    }
}
