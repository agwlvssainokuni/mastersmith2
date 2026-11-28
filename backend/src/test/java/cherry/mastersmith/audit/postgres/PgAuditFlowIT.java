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
package cherry.mastersmith.audit.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgSharedApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 監査の記録の種類と件数の代表の流れ（BR1.1 の 11。期待は {@code RoleAuditIT}・{@code UserAdminAuditIT}・{@code AuthEventsIT}）。 */
@InternalDbPostgresIT
class PgAuditFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    @Test
    @DisplayName(
            "each successful operation adds exactly one audit row of its type, and a failed login adds a FAILURE row")
    void successfulOperationsAreAudited() {
        PortableFixture.Account member = fixture.createUser(false);
        fixture.adminToken();
        int loginOk = fixture.auditCount("LOGIN_SUCCEEDED", "SUCCESS");
        int loginNg = fixture.auditCount("LOGIN_FAILED", "FAILURE");
        int roleCreated = fixture.auditCount("ROLE_CREATED", "SUCCESS");
        int roleAssigned = fixture.auditCount("ROLE_ASSIGNED", "SUCCESS");
        int groupCreated = fixture.auditCount("GROUP_CREATED", "SUCCESS");
        int groupMember = fixture.auditCount("GROUP_MEMBER_ADDED", "SUCCESS");
        int loggedOut = fixture.auditCount("LOGGED_OUT");

        fixture.loginResponse(member.email(), "wrong-" + member.password());
        String cookie = PortableFixture.cookieValue(fixture.loginResponse(member.email(), member.password()));
        long roleId = fixture.createRole(PortableFixture.uniqueName("audit"));
        fixture.assignRoleToUser(roleId, member.userId());
        long groupId = fixture.createGroup(PortableFixture.uniqueName("audit"));
        fixture.addMember(groupId, member.userId());
        fixture.logout(cookie, fixture.origin());

        assertThat(fixture.auditCount("LOGIN_FAILED", "FAILURE")).isEqualTo(loginNg + 1);
        assertThat(fixture.auditCount("LOGIN_SUCCEEDED", "SUCCESS")).isEqualTo(loginOk + 1);
        assertThat(fixture.auditCount("ROLE_CREATED", "SUCCESS")).isEqualTo(roleCreated + 1);
        assertThat(fixture.auditCount("ROLE_ASSIGNED", "SUCCESS")).isEqualTo(roleAssigned + 1);
        assertThat(fixture.auditCount("GROUP_CREATED", "SUCCESS")).isEqualTo(groupCreated + 1);
        assertThat(fixture.auditCount("GROUP_MEMBER_ADDED", "SUCCESS")).isEqualTo(groupMember + 1);
        assertThat(fixture.auditCount("LOGGED_OUT")).isEqualTo(loggedOut + 1);
    }

    @Test
    @DisplayName(
            "a rejected operation and an access denial are audited, and an unchanged assignment adds no SUCCESS row")
    void rejectedOperationsAreAudited() {
        PortableFixture.Account member = fixture.createUser(false);
        long roleId = fixture.createRole(PortableFixture.uniqueName("audit"));
        fixture.assignRoleToUser(roleId, member.userId());
        int denied = fixture.auditCount("ACCESS_DENIED");
        int assigned = fixture.auditCount("ROLE_ASSIGNED", "SUCCESS");

        PortableFixture.assertProblem(
                fixture.adminCall(
                        "POST",
                        "/api/admin/roles/" + roleId + "/assignments",
                        PortableFixture.json(Map.of("userId", member.userId()))),
                409,
                "ROLE_NO_CHANGE");
        PortableFixture.assertProblem(
                fixture.send("GET", "/api/admin/roles", fixture.login(member), null), 403, "ACCESS_DENIED");

        assertThat(fixture.auditCount("ROLE_ASSIGNED", "SUCCESS")).isEqualTo(assigned);
        assertThat(fixture.auditCount("ACCESS_DENIED")).isEqualTo(denied + 1);
    }
}
