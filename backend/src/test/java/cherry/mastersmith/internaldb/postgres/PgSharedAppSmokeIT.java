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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.ConflictProbe;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PgSharedApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.internaldb.testsupport.WaitWindow;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U3 の共有のテストの部品（{@link PgSharedApp}・{@link PortableFixture}・{@link ConflictProbe}・{@link WaitWindow}）が動くことの確かめ。
 * 11 の機能の流れのクラス（計画の Step 5）に吸収してよい。
 */
@InternalDbPostgresIT
class PgSharedAppSmokeIT {

    private static PgApp classApp;

    private PortableFixture fixture;

    private ConflictProbe probe;

    @BeforeAll
    static void startApp() {
        classApp = PgApp.start();
    }

    @AfterAll
    static void stopApp() {
        classApp.close();
    }

    @BeforeEach
    void setUp() {
        fixture = new PortableFixture(classApp);
        probe = new ConflictProbe(classApp.database());
    }

    @AfterEach
    void tearDown() {
        try {
            probe.releaseHolders();
            probe.awaitNoWaiters();
        } finally {
            probe.close();
        }
    }

    @Test
    @DisplayName("the shared app starts once, is reused, and the initial admin can log in")
    void sharedAppIsReusedAndAdminLogsIn() {
        PgApp shared = PgSharedApp.get();
        PortableFixture sharedFixture = new PortableFixture(shared);

        HttpResponse<String> login = sharedFixture.loginResponse(shared.adminEmail(), shared.adminPassword());

        assertThat(PgSharedApp.get()).isSameAs(shared);
        assertThat(shared.startupMillis()).isPositive();
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(login)).containsKey("accessToken");
    }

    @Test
    @DisplayName(
            "the fixture creates users, roles, groups, assignments, an invitation and a DSL through the API and SQL")
    void fixtureBuildsPrerequisites() {
        PortableFixture.Account member = fixture.createUser(false);
        long roleId = fixture.createRole(PortableFixture.uniqueName("role"));
        long groupId = fixture.createGroup(PortableFixture.uniqueName("group"));
        fixture.addMember(groupId, member.userId());
        fixture.assignRoleToUser(roleId, member.userId());
        long otherRole = fixture.createRole(PortableFixture.uniqueName("role"));
        fixture.assignRoleToGroup(otherRole, groupId);
        PortableFixture.Invitation invitation =
                fixture.insertInvitation(member.userId(), Instant.now().plus(1, ChronoUnit.HOURS));
        String previewId = fixture.placePreview(PortableFixture.minimalDsl("pg_smoke"));
        fixture.applyPreview(previewId);

        assertThat(fixture.login(member)).isNotBlank();
        HttpResponse<String> verified = fixture.send(
                "POST", "/api/registration/verify", null, PortableFixture.json(Map.of("token", invitation.token())));
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(fixture.adminCall("GET", "/api/admin/roles/" + roleId + "/assignments", null)
                        .statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName(
            "a holder connection makes an app write wait, the wait is observed, and it ends within the write window")
    void holderMakesAppWriteWait() throws Exception {
        PortableFixture.Account member = fixture.createUser(false);
        String token = fixture.login(member);
        Connection holder = probe.openHolder();
        ConflictProbe.execute(holder, "SELECT user_id FROM users WHERE user_id = " + member.userId() + " FOR UPDATE");

        Future<WaitWindow.Timed<HttpResponse<String>>> waiting = probe.asyncTimed(() -> fixture.send(
                "PUT",
                "/api/me/preferences",
                token,
                PortableFixture.json(
                        Map.of("displayName", "PG Member", "language", "ja", "theme", "system", "fontSize", "md"))));
        probe.awaitWaiting();
        WaitWindow.Timed<HttpResponse<String>> result = waiting.get();
        probe.release(holder);

        WaitWindow.expectWrite("PgSharedAppSmokeIT.holderMakesAppWriteWait", result.millis());
        assertThat(result.value().statusCode()).isEqualTo(500);
    }
}
