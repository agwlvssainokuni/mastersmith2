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
import tools.jackson.databind.json.JsonMapper;
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
}
