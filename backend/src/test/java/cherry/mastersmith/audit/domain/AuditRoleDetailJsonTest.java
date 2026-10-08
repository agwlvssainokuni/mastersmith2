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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditDetail.PermissionChange;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** ロールの操作の detail の JSON（BR11.5・BR11.6・BR11.8、NFR5.5、計画の D-11）の単体テスト。 */
class AuditRoleDetailJsonTest {

    private static final String EMOJI = "😀";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String detail) {
        return JsonMapper.shared().readValue(detail, Map.class);
    }

    private static PermissionChange change(String column) {
        return new PermissionChange(
                PermissionTarget.column("SALES", "ORDER_LINE", column),
                new PermissionValues(MainPermission.READ, null, null),
                new PermissionValues(MainPermission.NONE, null, null));
    }

    /** 全件の JSON の長さがちょうど n になる変更の一覧を、名前の長さを合わせて作る。 */
    private static RoleAuditDetail.PermissionChanges changesOfLength(int length) {
        RoleAuditDetail.PermissionChanges base = new RoleAuditDetail.PermissionChanges("r", List.of(change("c")));
        int baseLength = AuditDetailJson.ofRole(base).length();
        return new RoleAuditDetail.PermissionChanges("r", List.of(change("c" + "x".repeat(length - baseLength))));
    }

    @Test
    @DisplayName("the simple shapes produce only their decided keys")
    void simpleShapes() {
        assertThat(json(AuditDetailJson.ofRole(new RoleAuditDetail.Name("営業")))).isEqualTo(Map.of("name", "営業"));
        assertThat(json(AuditDetailJson.ofRole(new RoleAuditDetail.Rename("Sales", "SALES"))))
                .isEqualTo(Map.of("before", "Sales", "after", "SALES"));
        assertThat(json(AuditDetailJson.ofRole(new RoleAuditDetail.InUse("営業", 3, 2))))
                .isEqualTo(Map.of("name", "営業", "assignedUsers", 3, "assignedGroups", 2));
        assertThat(AuditDetailJson.ofRole(null)).isNull();
    }

    @Test
    @DisplayName("a permission change has the target and the before and after values with null for not set")
    void permissionChanges() {
        String detail = AuditDetailJson.ofRole(new RoleAuditDetail.PermissionChanges(
                "営業",
                List.of(new PermissionChange(
                        PermissionTarget.table("SALES", "ORDER_LINE"),
                        PermissionValues.NOT_SET,
                        new PermissionValues(MainPermission.FULL, true, null)))));

        assertThat(detail)
                .isEqualTo("{\"roleName\":\"営業\",\"changes\":[{\"target\":{\"schemaName\":\"SALES\","
                        + "\"tableName\":\"ORDER_LINE\",\"columnName\":null},\"before\":{\"main\":null,\"create\":null,"
                        + "\"delete\":null},\"after\":{\"main\":\"FULL\",\"create\":true,\"delete\":null}}]}");
    }

    @Test
    @DisplayName("exactly 16,384 characters keep all changes and one more character switches to the summary")
    void boundary() {
        String exact = AuditDetailJson.ofRole(changesOfLength(AuditEvent.MAX_DETAIL_LENGTH));
        String over = AuditDetailJson.ofRole(changesOfLength(AuditEvent.MAX_DETAIL_LENGTH + 1));

        assertThat(exact).hasSize(AuditEvent.MAX_DETAIL_LENGTH);
        assertThat(json(exact)).containsKey("changes");
        assertThat(json(over))
                .containsKeys("roleName", "changedCount", "firstChanges")
                .doesNotContainKey("changes");
        assertThat(over.length()).isLessThanOrEqualTo(AuditEvent.MAX_DETAIL_LENGTH);
    }

    @Test
    @DisplayName("a summary keeps at most 20 first changes and fewer when long names would exceed the limit")
    void summaryFitsTheLimit() {
        RoleAuditDetail.PermissionChanges many = new RoleAuditDetail.PermissionChanges(
                "営業",
                IntStream.range(0, 500).mapToObj(i -> change("COLUMN_" + i)).toList());
        String longName = EMOJI.repeat(128);
        RoleAuditDetail.PermissionChanges longNames = new RoleAuditDetail.PermissionChanges(
                "営業",
                IntStream.range(0, 100)
                        .mapToObj(i -> new PermissionChange(
                                PermissionTarget.column(longName, longName, longName + i),
                                PermissionValues.NOT_SET,
                                new PermissionValues(MainPermission.READ, null, null)))
                        .toList());

        Map<String, Object> manySummary = json(AuditDetailJson.ofRole(many));
        String longSummary = AuditDetailJson.ofRole(longNames);

        assertThat(manySummary).containsEntry("changedCount", 500);
        assertThat((List<?>) manySummary.get("firstChanges")).hasSize(AuditDetailJson.SUMMARY_FIRST_CHANGES);
        assertThat(longSummary.length()).isLessThanOrEqualTo(AuditEvent.MAX_DETAIL_LENGTH);
        assertThat(json(longSummary)).containsEntry("changedCount", 100);
        assertThat((List<?>) json(longSummary).get("firstChanges")).hasSizeBetween(1, 19);
    }

    @Test
    @DisplayName("names with quotes and markup are escaped as JSON strings and read back unchanged")
    void escaping() {
        String name = "\"営業\" <b>&'部'";

        assertThat(json(AuditDetailJson.ofRole(new RoleAuditDetail.Name(name)))).isEqualTo(Map.of("name", name));
    }
}
