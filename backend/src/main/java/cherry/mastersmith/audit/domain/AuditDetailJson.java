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
package cherry.mastersmith.audit.domain;

import cherry.mastersmith.group.domain.GroupAuditDetail;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 監査の {@code detail} の JSON を、決めた型の中身から決めたキーだけで作る（Intent 261004-role-menu の U3、契約 C10、BR8.5・BR8.7、
 * {@code observability-design.md} 1節、計画の D-11）。DB にも時計にも触れない純粋な関数である。
 *
 * <p>キーは形ごとに決まっている（{@code Name} は {@code name}、{@code Rename} は {@code before}・{@code after}、{@code Membership} は
 * {@code groupName}、{@code InUse} は {@code name}・{@code members}・{@code assignedRoles}）。自由な対応表や任意の文字列の連結は受けず、
 * 値は Jackson が JSON の文字列としてエスケープする。メールアドレス・氏名・秘密は型に無いため、JSON にも入らない。形の写し取りは網羅の
 * {@code switch} で書く（形が増えたときにコンパイルで気づけるようにするため）。
 */
public final class AuditDetailJson {

    /** 権限の変更の要約に入れる先頭の件数の上限（{@code observability-design.md} 1節。上限に収まるまで減らす。計画の D-11）。 */
    public static final int SUMMARY_FIRST_CHANGES = 20;

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private AuditDetailJson() {}

    /**
     * グループの操作の detail を JSON の文字列にする。
     *
     * @param detail detail の中身（無ければ null）
     * @return JSON の文字列（中身が無ければ null）
     */
    public static String of(GroupAuditDetail detail) {
        if (detail == null) {
            return null;
        }
        ObjectNode node = FACTORY.objectNode();
        switch (detail) {
            case GroupAuditDetail.Name(String name) -> node.put("name", name);
            case GroupAuditDetail.Rename(String before, String after) ->
                node.put("before", before).put("after", after);
            case GroupAuditDetail.Membership(String groupName) -> node.put("groupName", groupName);
            case GroupAuditDetail.InUse(String name, int members, int assignedRoles) ->
                node.put("name", name).put("members", members).put("assignedRoles", assignedRoles);
        }
        return JsonMapper.shared().writeValueAsString(node);
    }

    /**
     * ロールの操作の detail を JSON の文字列にする（Intent 261004-role-menu の U4、BR11.5・BR11.6・BR11.8、計画の D-11）。
     *
     * <p>キーは形ごとに決まっている（{@code Name} は {@code name}、{@code Rename} は {@code before}・{@code after}、{@code InUse} は
     * {@code name}・{@code assignedUsers}・{@code assignedGroups}、{@code PermissionChanges} は {@code roleName}・{@code changes}、
     * {@code PermissionChangesSummary} は {@code roleName}・{@code changedCount}・{@code firstChanges}）。1つの変更は
     * {@code target}（{@code schemaName}・{@code tableName}・{@code columnName}）・{@code before}・{@code after}（{@code main}・
     * {@code create}・{@code delete}、設定なしは null）。
     *
     * <p>権限の変更は全件の JSON を作ってから長さ（{@link String#length()}）を見て、{@link AuditEvent#MAX_DETAIL_LENGTH} を超えれば要約に
     * 切り替える。要約の先頭の件数は {@value #SUMMARY_FIRST_CHANGES} 件から、上限に収まるまで減らす（途中で切らない）。
     *
     * @param detail detail の中身（無ければ null）
     * @return JSON の文字列（中身が無ければ null）
     */
    public static String ofRole(RoleAuditDetail detail) {
        if (detail == null) {
            return null;
        }
        String json = write(roleNode(detail));
        if (json.length() <= AuditEvent.MAX_DETAIL_LENGTH
                || !(detail instanceof RoleAuditDetail.PermissionChanges changes)) {
            return json;
        }
        for (int first = Math.min(SUMMARY_FIRST_CHANGES, changes.changes().size()); first >= 0; first--) {
            String summary = write(roleNode(changes.summarize(first)));
            if (summary.length() <= AuditEvent.MAX_DETAIL_LENGTH) {
                return summary;
            }
        }
        throw new IllegalStateException("要約が上限に収まりません");
    }

    private static ObjectNode roleNode(RoleAuditDetail detail) {
        ObjectNode node = FACTORY.objectNode();
        switch (detail) {
            case RoleAuditDetail.Name(String name) -> node.put("name", name);
            case RoleAuditDetail.Rename(String before, String after) ->
                node.put("before", before).put("after", after);
            case RoleAuditDetail.InUse(String name, int assignedUsers, int assignedGroups) ->
                node.put("name", name).put("assignedUsers", assignedUsers).put("assignedGroups", assignedGroups);
            case RoleAuditDetail.PermissionChanges(String roleName, List<RoleAuditDetail.PermissionChange> changes) -> {
                node.put("roleName", roleName);
                node.set("changes", changesNode(changes));
            }
            case RoleAuditDetail.PermissionChangesSummary(
                    String roleName,
                    int changedCount,
                    List<RoleAuditDetail.PermissionChange> firstChanges) -> {
                node.put("roleName", roleName).put("changedCount", changedCount);
                node.set("firstChanges", changesNode(firstChanges));
            }
        }
        return node;
    }

    private static ArrayNode changesNode(List<RoleAuditDetail.PermissionChange> changes) {
        ArrayNode array = FACTORY.arrayNode();
        for (RoleAuditDetail.PermissionChange change : changes) {
            ObjectNode item = array.addObject();
            item.set("target", targetNode(change.target()));
            item.set("before", valuesNode(change.before()));
            item.set("after", valuesNode(change.after()));
        }
        return array;
    }

    private static ObjectNode targetNode(PermissionTarget target) {
        ObjectNode node = FACTORY.objectNode();
        node.put("schemaName", target.schemaName());
        node.put("tableName", target.tableName());
        node.put("columnName", target.columnName());
        return node;
    }

    private static ObjectNode valuesNode(PermissionValues values) {
        ObjectNode node = FACTORY.objectNode();
        MainPermission main = values.main();
        node.put("main", main == null ? null : main.name());
        node.put("create", values.create());
        node.put("delete", values.delete());
        return node;
    }

    private static String write(ObjectNode node) {
        return JsonMapper.shared().writeValueAsString(node);
    }
}
