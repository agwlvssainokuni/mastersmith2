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
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.TransferAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 検証を通った権限の YAML と今のロール・設定から、変わる点の一覧と指紋を作る純粋な関数（BR9.6〜BR9.9）。DB と DSL に触れず、今の状態と
 * 今の DSL にあるかの判定は呼び出し元が渡す。
 *
 * <ul>
 *   <li>ファイルのロールを名前の鍵で今のロールと照らし、当たれば置き換え（名前は変えない）、当たらなければ作る。ファイルに無いロールは
 *       残し、名前を {@code untouchedRoles} に並べる（BR9.6）
 *   <li>置き換えるロールは、今の行のうちファイルに無いものを CLEARED、値が違うものを CHANGED、無いものを ADDED にする。作るロールは
 *       ファイルの設定をすべて ADDED にする（BR9.7）
 *   <li>今の DSL に無い対象も名前で持つ設定として扱い、{@code inCurrentDsl} を付ける（BR9.8）
 *   <li>ロールは鍵のコードポイントの順、変わる対象は対象の順に並べ、指紋を作る（BR9.9）
 * </ul>
 */
public final class TransferPlanner {

    private TransferPlanner() {}

    /**
     * 今のロール（照らすための名前と鍵、置き換えに当たるときは設定）。
     *
     * @param roleId ロールの ID
     * @param name 今の名前
     * @param nameKey 名前の鍵
     * @param settings 対象ごとの今の値（置き換えに当たらないロールは空でよい）
     */
    public record CurrentRole(
            long roleId, String name, String nameKey, Map<PermissionTarget, PermissionValues> settings) {

        /** 値が null でないことを確かめ、写して持つ。 */
        public CurrentRole {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(nameKey, "nameKey");
            settings = Map.copyOf(settings);
        }

        /** ID と設定の数だけを出す。 */
        @Override
        public String toString() {
            return "CurrentRole[roleId=" + roleId + ", settings=" + settings.size() + "]";
        }
    }

    /**
     * 変わる点の一覧を作る。
     *
     * @param document 検証を通った YAML の中身
     * @param current 今のロール（どの順でもよい）
     * @param inCurrentDsl 今の DSL にある対象かの判定
     * @param dslHash 適用中の DSL の識別（無ければ null）
     * @return 変わる点の一覧と指紋
     */
    public static TransferPlan plan(
            RoleTransferDocument document,
            List<CurrentRole> current,
            Predicate<PermissionTarget> inCurrentDsl,
            String dslHash) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(inCurrentDsl, "inCurrentDsl");
        Map<String, CurrentRole> byKey = new HashMap<>();
        for (CurrentRole role : current) {
            byKey.put(role.nameKey(), role);
        }
        List<RoleTransferDocument.Role> fileRoles = new ArrayList<>(document.roles());
        fileRoles.sort(Comparator.comparing(role -> role.name().key(), TransferOrder.NAME));
        List<TransferRolePlan> roles = new ArrayList<>();
        Set<String> fileKeys = new HashSet<>();
        for (RoleTransferDocument.Role fileRole : fileRoles) {
            RoleName name = fileRole.name();
            fileKeys.add(name.key());
            CurrentRole matched = byKey.get(name.key());
            if (matched == null) {
                roles.add(new TransferRolePlan(
                        name.value(),
                        name,
                        null,
                        TransferAction.CREATE,
                        changes(Map.of(), fileRole.settings(), inCurrentDsl)));
            } else {
                roles.add(new TransferRolePlan(
                        matched.name(),
                        name,
                        matched.roleId(),
                        TransferAction.REPLACE,
                        changes(matched.settings(), fileRole.settings(), inCurrentDsl)));
            }
        }
        List<String> untouched = current.stream()
                .filter(role -> !fileKeys.contains(role.nameKey()))
                .sorted(Comparator.comparingLong(CurrentRole::roleId))
                .map(CurrentRole::name)
                .collect(Collectors.toList());
        return new TransferPlan(TransferFingerprint.of(roles, dslHash), roles, untouched);
    }

    private static List<TransferChange> changes(
            Map<PermissionTarget, PermissionValues> before,
            Map<PermissionTarget, PermissionValues> after,
            Predicate<PermissionTarget> inCurrentDsl) {
        Set<PermissionTarget> targets = new TreeSet<>(TransferOrder.TARGET);
        targets.addAll(before.keySet());
        targets.addAll(after.keySet());
        List<TransferChange> changes = new ArrayList<>();
        for (PermissionTarget target : targets) {
            PermissionValues was = PermissionValues.orNotSet(before.get(target));
            PermissionValues will = PermissionValues.orNotSet(after.get(target));
            if (was.equals(will)) {
                continue;
            }
            TransferChange.Reason reason = was.isEmpty()
                    ? TransferChange.Reason.ADDED
                    : will.isEmpty() ? TransferChange.Reason.CLEARED : TransferChange.Reason.CHANGED;
            changes.add(new TransferChange(target, was, will, reason, inCurrentDsl.test(target)));
        }
        return changes;
    }
}
