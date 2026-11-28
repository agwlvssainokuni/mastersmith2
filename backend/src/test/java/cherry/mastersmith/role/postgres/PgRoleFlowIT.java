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
package cherry.mastersmith.role.postgres;

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

/** ロールと権限の割り当ての代表の流れ（BR1.1 の 5。期待は {@code RoleAdminApiIT}・{@code RoleAssignmentApiIT}）。 */
@InternalDbPostgresIT
class PgRoleFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    @Test
    @DisplayName("an admin creates a role, assigns it to a user and unassigns it, and deletes the role with 204")
    void createAssignAndDelete() {
        PortableFixture.Account member = fixture.createUser(false);
        String name = PortableFixture.uniqueName("role");
        int created = fixture.auditCount("ROLE_CREATED", "SUCCESS");
        int assigned = fixture.auditCount("ROLE_ASSIGNED", "SUCCESS");

        HttpResponse<String> create =
                fixture.adminCall("POST", "/api/admin/roles", PortableFixture.json(Map.of("name", name)));
        assertThat(create.statusCode()).isEqualTo(201);
        assertThat(HttpTestClient.json(create))
                .containsOnlyKeys("roleId", "name", "createdAt", "updatedAt")
                .containsEntry("name", name);
        long roleId = PortableFixture.idOf(create);
        String assignments = "/api/admin/roles/" + roleId + "/assignments";

        assertThat(fixture.adminCall("POST", assignments, PortableFixture.json(Map.of("userId", member.userId())))
                        .statusCode())
                .isEqualTo(204);
        assertThat(fixture.adminCall("GET", assignments, null).statusCode()).isEqualTo(200);
        assertThat(fixture.adminCall("DELETE", assignments + "/users/" + member.userId(), null)
                        .statusCode())
                .isEqualTo(204);
        assertThat(fixture.adminCall("DELETE", "/api/admin/roles/" + roleId, null)
                        .statusCode())
                .isEqualTo(204);

        assertThat(fixture.auditCount("ROLE_CREATED", "SUCCESS")).isEqualTo(created + 1);
        assertThat(fixture.auditCount("ROLE_ASSIGNED", "SUCCESS")).isEqualTo(assigned + 1);
    }

    @Test
    @DisplayName(
            "a duplicate name, an unchanged assignment, a missing target and a role in use are rejected with the same status and code as on H2")
    void failures() {
        PortableFixture.Account member = fixture.createUser(false);
        String name = PortableFixture.uniqueName("role");
        long roleId = fixture.createRole(name);
        fixture.assignRoleToUser(roleId, member.userId());
        String assignments = "/api/admin/roles/" + roleId + "/assignments";

        PortableFixture.assertProblem(
                fixture.adminCall("POST", "/api/admin/roles", PortableFixture.json(Map.of("name", name))),
                409,
                "ROLE_NAME_DUPLICATE");
        PortableFixture.assertProblem(
                fixture.adminCall("POST", assignments, PortableFixture.json(Map.of("userId", member.userId()))),
                409,
                "ROLE_NO_CHANGE");
        PortableFixture.assertProblem(
                fixture.adminCall("POST", assignments, PortableFixture.json(Map.of("userId", Long.MAX_VALUE))),
                404,
                "USER_NOT_FOUND");
        PortableFixture.assertProblem(
                fixture.adminCall("GET", "/api/admin/roles/" + Long.MAX_VALUE + "/assignments", null),
                404,
                "ROLE_NOT_FOUND");
        PortableFixture.assertProblem(
                fixture.adminCall("DELETE", "/api/admin/roles/" + roleId, null), 409, "ROLE_IN_USE");
        PortableFixture.assertProblem(
                fixture.send("GET", "/api/admin/roles", fixture.login(member), null), 403, "ACCESS_DENIED");
    }
}
