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

/** 作業ロールの選択の代表の流れ（BR1.1 の 6。期待は {@code WorkRoleSwitchApiIT}）。 */
@InternalDbPostgresIT
class PgWorkRoleFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    @SuppressWarnings("unchecked")
    private static long currentRoleId(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> current =
                (Map<String, Object>) HttpTestClient.json(response).get("current");
        return ((Number) current.get("roleId")).longValue();
    }

    @Test
    @DisplayName(
            "the first assigned role is the work role, and switching to another assigned role is saved with 204 and audited")
    void switchesWorkRole() {
        PortableFixture.Account member = fixture.createUser(false);
        long first = fixture.createRole(PortableFixture.uniqueName("work"));
        long second = fixture.createRole(PortableFixture.uniqueName("work"));
        fixture.assignRoleToUser(first, member.userId());
        fixture.assignRoleToUser(second, member.userId());
        String token = fixture.login(member);
        int switched = fixture.auditCount("WORK_ROLE_SWITCHED", "SUCCESS");

        assertThat(currentRoleId(fixture.send("GET", "/api/me/work-role", token, null)))
                .isEqualTo(first);
        assertThat(fixture.send("PUT", "/api/me/work-role", token, PortableFixture.json(Map.of("roleId", second)))
                        .statusCode())
                .isEqualTo(204);

        assertThat(currentRoleId(fixture.send("GET", "/api/me/work-role", token, null)))
                .isEqualTo(second);
        assertThat(fixture.auditCount("WORK_ROLE_SWITCHED", "SUCCESS")).isEqualTo(switched + 1);
    }

    @Test
    @DisplayName("a role that is not assigned is 409 ROLE_NOT_ASSIGNED and a missing roleId is 400 VALIDATION_FAILED")
    void failures() {
        PortableFixture.Account member = fixture.createUser(false);
        long assigned = fixture.createRole(PortableFixture.uniqueName("work"));
        long outside = fixture.createRole(PortableFixture.uniqueName("work"));
        fixture.assignRoleToUser(assigned, member.userId());
        String token = fixture.login(member);

        PortableFixture.assertProblem(
                fixture.send("PUT", "/api/me/work-role", token, PortableFixture.json(Map.of("roleId", outside))),
                409,
                "ROLE_NOT_ASSIGNED");
        PortableFixture.assertProblem(fixture.send("PUT", "/api/me/work-role", token, "{}"), 400, "VALIDATION_FAILED");
        assertThat(currentRoleId(fixture.send("GET", "/api/me/work-role", token, null)))
                .isEqualTo(assigned);
    }
}
