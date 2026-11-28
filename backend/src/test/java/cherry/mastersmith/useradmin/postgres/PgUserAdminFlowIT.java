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
package cherry.mastersmith.useradmin.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgSharedApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 利用者の管理（止める・戻す・ロックの解除）の代表の流れ（BR1.1 の 3。期待は {@code UserAdminOperationsApiIT}）。 */
@InternalDbPostgresIT
class PgUserAdminFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    private static String operation(long userId, String action) {
        return "/api/admin/users/" + userId + "/" + action;
    }

    @Test
    @DisplayName(
            "an admin suspends, resumes and resets the login failures of a member with 204 and the member's login follows")
    void suspendResumeAndReset() {
        PortableFixture.Account member = fixture.createUser(false);
        String refresh = PortableFixture.cookieValue(fixture.loginResponse(member.email(), member.password()));
        int suspended = fixture.auditCount("USER_SUSPENDED", "SUCCESS");
        int resumed = fixture.auditCount("USER_RESUMED", "SUCCESS");
        int reset = fixture.auditCount("LOGIN_FAILURES_RESET", "SUCCESS");

        assertThat(fixture.adminCall("POST", operation(member.userId(), "suspend"), null)
                        .statusCode())
                .isEqualTo(204);
        PortableFixture.assertProblem(
                fixture.loginResponse(member.email(), member.password()), 401, "AUTHENTICATION_FAILED");
        PortableFixture.assertProblem(fixture.refresh(refresh, fixture.origin()), 401, "REFRESH_FAILED");
        assertThat(fixture.adminCall("POST", operation(member.userId(), "resume"), null)
                        .statusCode())
                .isEqualTo(204);
        assertThat(fixture.loginResponse(member.email(), member.password()).statusCode())
                .isEqualTo(200);

        for (int i = 0; i < 5; i++) {
            fixture.loginResponse(member.email(), "wrong-" + member.password());
        }
        PortableFixture.assertProblem(
                fixture.loginResponse(member.email(), member.password()), 401, "AUTHENTICATION_FAILED");
        assertThat(fixture.adminCall("POST", operation(member.userId(), "reset-login-failures"), null)
                        .statusCode())
                .isEqualTo(204);
        assertThat(fixture.loginResponse(member.email(), member.password()).statusCode())
                .isEqualTo(200);

        assertThat(fixture.auditCount("USER_SUSPENDED", "SUCCESS")).isEqualTo(suspended + 1);
        assertThat(fixture.auditCount("USER_RESUMED", "SUCCESS")).isEqualTo(resumed + 1);
        assertThat(fixture.auditCount("LOGIN_FAILURES_RESET", "SUCCESS")).isEqualTo(reset + 1);
    }

    @Test
    @DisplayName(
            "a self operation, a no-change operation and a non-admin caller are rejected with the same status and code as on H2")
    void failures() {
        PortableFixture.Account member = fixture.createUser(false);
        PortableFixture.Account other = fixture.createUser(false);

        PortableFixture.assertProblem(
                fixture.adminCall("POST", operation(fixture.adminUserId(), "suspend"), null),
                409,
                "USER_ADMIN_SELF_OPERATION");
        PortableFixture.assertProblem(
                fixture.adminCall("POST", operation(member.userId(), "resume"), null), 409, "USER_ADMIN_NO_CHANGE");
        PortableFixture.assertProblem(
                fixture.send("POST", operation(other.userId(), "suspend"), fixture.login(member), null),
                403,
                "ACCESS_DENIED");
        assertThat(fixture.send("POST", operation(other.userId(), "suspend"), null, null)
                        .statusCode())
                .isEqualTo(401);
    }
}
