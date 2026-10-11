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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.transfer.TransferChange;
import cherry.mastersmith.role.transfer.TransferPlan;
import cherry.mastersmith.role.transfer.TransferRolePlan;
import java.util.List;

/**
 * 権限の YAML の確かめの応答（契約 C7 の TransferCheck。各ロールに {@code roleId}、各変更に {@code inCurrentDsl} を足した形。FS の 8節）。
 *
 * <p>ロールと対象の名前を含むため、{@link #toString()} はロールの数と変わる対象の数だけを出す（{@code TraceAspect} が画面入出力の層の
 * 戻り値を TRACE に出すため。NFR1.9）。
 *
 * @param fingerprint 指紋（適用のときに {@code X-Role-Transfer-Fingerprint} で送る）
 * @param roles ロールごとの変わる点（ロールの鍵の順）
 * @param untouchedRoles ファイルに無い今のロールの名前（ロールの ID の順）
 */
public record TransferCheckResponse(String fingerprint, List<Role> roles, List<String> untouchedRoles) {

    /**
     * 1つのロールの変わる点。
     *
     * @param name 名前
     * @param roleId 置き換えるロールの ID（作るときは null）
     * @param action {@code CREATE}・{@code REPLACE}
     * @param added 足す対象の数
     * @param changed 値が変わる対象の数
     * @param cleared 設定なしになる対象の数
     * @param notInDsl 変わる対象のうち今の DSL に無いものの数
     * @param changes 変わる対象（対象の順）
     */
    public record Role(
            String name,
            Long roleId,
            String action,
            int added,
            int changed,
            int cleared,
            int notInDsl,
            List<Change> changes) {}

    /**
     * 1つの対象の変わり方。
     *
     * @param target 対象
     * @param before 前の値
     * @param after 後の値
     * @param reason {@code ADDED}・{@code CHANGED}・{@code CLEARED}
     * @param inCurrentDsl 今の DSL にあるか
     */
    public record Change(Target target, Values before, Values after, String reason, boolean inCurrentDsl) {}

    /**
     * 対象（下位の名前が無い階層は null）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @param columnName カラムの名前
     */
    public record Target(String schemaName, String tableName, String columnName) {}

    /**
     * 値（設定なしは null）。
     *
     * @param main 主権限
     * @param create 補助権限 CREATE
     * @param delete 補助権限 DELETE
     */
    public record Values(String main, Boolean create, Boolean delete) {}

    /**
     * 変わる点の一覧から作る。
     *
     * @param plan 変わる点の一覧
     * @return 応答
     */
    static TransferCheckResponse from(TransferPlan plan) {
        return new TransferCheckResponse(
                plan.fingerprint(),
                plan.roles().stream().map(TransferCheckResponse::role).toList(),
                plan.untouchedRoles());
    }

    private static Role role(TransferRolePlan role) {
        return new Role(
                role.name(),
                role.roleId(),
                role.action().name(),
                role.count(TransferChange.Reason.ADDED),
                role.count(TransferChange.Reason.CHANGED),
                role.count(TransferChange.Reason.CLEARED),
                role.notInDsl(),
                role.changes().stream().map(TransferCheckResponse::change).toList());
    }

    private static Change change(TransferChange change) {
        PermissionTarget target = change.target();
        return new Change(
                new Target(target.schemaName(), target.tableName(), target.columnName()),
                values(change.before()),
                values(change.after()),
                change.reason().name(),
                change.inCurrentDsl());
    }

    private static Values values(PermissionValues values) {
        MainPermission main = values.main();
        return new Values(main == null ? null : main.name(), values.create(), values.delete());
    }

    /** ロールの数と変わる対象の数だけを出す。 */
    @Override
    public String toString() {
        return "TransferCheckResponse[roles=" + roles.size() + ", changes="
                + roles.stream().mapToInt(role -> role.changes().size()).sum() + "]";
    }
}
