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
package cherry.mastersmith.group.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgSharedApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** グループの代表の流れ（BR1.1 の 7。期待は {@code GroupAdminApiIT}）。 */
@InternalDbPostgresIT
class PgGroupFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    @Test
    @DisplayName(
            "an admin creates a group, adds and removes a member and deletes the group, with the same responses as on H2")
    void createMembersAndDelete() {
        PortableFixture.Account member = fixture.createUser(false);
        String name = PortableFixture.uniqueName("group");
        int created = fixture.auditCount("GROUP_CREATED", "SUCCESS");

        HttpResponse<String> create =
                fixture.adminCall("POST", "/api/admin/groups", PortableFixture.json(Map.of("name", name)));
        assertThat(create.statusCode()).isEqualTo(201);
        assertThat(HttpTestClient.json(create))
                .containsOnlyKeys("groupId", "name", "createdAt", "updatedAt")
                .containsEntry("name", name);
        long groupId = PortableFixture.idOf(create);
        String members = "/api/admin/groups/" + groupId + "/members";

        assertThat(fixture.adminCall("POST", members, PortableFixture.json(Map.of("userId", member.userId())))
                        .statusCode())
                .isEqualTo(204);
        HttpResponse<String> detail = fixture.adminCall("GET", "/api/admin/groups/" + groupId, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(detail)).containsOnlyKeys("groupId", "name", "members");
        assertThat(HttpTestClient.json(detail).get("members"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsOnlyKeys("userId", "displayName", "email", "suspended");
        assertThat(fixture.adminCall("DELETE", members + "/" + member.userId(), null)
                        .statusCode())
                .isEqualTo(204);
        assertThat(fixture.adminCall("DELETE", "/api/admin/groups/" + groupId, null)
                        .statusCode())
                .isEqualTo(204);

        PortableFixture.assertProblem(
                fixture.adminCall("GET", "/api/admin/groups/" + groupId, null), 404, "GROUP_NOT_FOUND");
        assertThat(fixture.auditCount("GROUP_CREATED", "SUCCESS")).isEqualTo(created + 1);
    }

    @Test
    @DisplayName(
            "a duplicate name, a missing user and a group in use are rejected with the same status and code as on H2")
    void failures() {
        String name = PortableFixture.uniqueName("group");
        long groupId = fixture.createGroup(name);
        long roleId = fixture.createRole(PortableFixture.uniqueName("role"));
        fixture.assignRoleToGroup(roleId, groupId);

        PortableFixture.assertProblem(
                fixture.adminCall("POST", "/api/admin/groups", PortableFixture.json(Map.of("name", name))),
                409,
                "GROUP_NAME_DUPLICATE");
        PortableFixture.assertProblem(
                fixture.adminCall(
                        "POST",
                        "/api/admin/groups/" + groupId + "/members",
                        PortableFixture.json(Map.of("userId", Long.MAX_VALUE))),
                404,
                "USER_NOT_FOUND");
        PortableFixture.assertProblem(
                fixture.adminCall("DELETE", "/api/admin/groups/" + groupId, null), 409, "GROUP_IN_USE");
    }
}
