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

import cherry.mastersmith.internaldb.testsupport.ConflictProbe;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.internaldb.testsupport.WaitWindow;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 行の排他の読み取りの待ち（BR3.2）。別の接続が行を排他で持つと、行の排他の読み取り（ヒントの 3 秒）は呼び出しの開始から 3 秒以上 4 秒未満で切れて
 * 409 {@code USER_ADMIN_BUSY}（{@code UserAdminBusyApiIT} の区分）になり、切れた後の同じ接続の書き込みは接続の上限 2 秒で切れる（ヒントの上限が
 * 接続に残らない）。同じ接続を確かめるため、プールを 2 本にして 1 本をテストが借り切り、アプリには 1 本だけを残す。
 */
@InternalDbPostgresIT
class PgRowLockWaitIT {

    private static PgApp app;

    private static PortableFixture fixture;

    private ConflictProbe probe;

    @BeforeAll
    static void start() {
        app = PgApp.start("--spring.datasource.hikari.maximum-pool-size=2");
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @BeforeEach
    void setUp() {
        probe = new ConflictProbe(app.database());
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
    @DisplayName(
            "a row lock read ends within [3s, 4s) as 409 USER_ADMIN_BUSY and the next write on the same connection ends within [2s, 3s)")
    void rowLockReadThenWrite() throws Exception {
        PortableFixture.Account target = fixture.createUser(false);
        PortableFixture.Account member = fixture.createUser(false);
        long first = fixture.createRole(PortableFixture.uniqueName("lock"));
        long second = fixture.createRole(PortableFixture.uniqueName("lock"));
        fixture.assignRoleToUser(first, member.userId());
        fixture.assignRoleToUser(second, member.userId());
        String adminToken = fixture.adminToken();
        String memberToken = fixture.login(member);
        assertThat(fixture.send("PUT", "/api/me/work-role", memberToken, PortableFixture.json(Map.of("roleId", second)))
                        .statusCode())
                .isEqualTo(204);

        try (Connection hog = app.context().getBean(DataSource.class).getConnection()) {
            assertThat(hog.isValid(2)).isTrue();
            Connection firstHolder = probe.openHolder();
            ConflictProbe.execute(
                    firstHolder, "SELECT user_id FROM users WHERE user_id = " + target.userId() + " FOR UPDATE");
            Future<WaitWindow.Timed<HttpResponse<String>>> suspending = probe.asyncTimed(
                    () -> fixture.send("POST", "/api/admin/users/" + target.userId() + "/suspend", adminToken, null));
            probe.awaitWaiting();
            WaitWindow.Timed<HttpResponse<String>> readResult = suspending.get(15, TimeUnit.SECONDS);
            probe.release(firstHolder);

            WaitWindow.expectRowLock("PgRowLockWaitIT.rowLockRead", readResult.millis());
            PortableFixture.assertProblem(readResult.value(), 409, "USER_ADMIN_BUSY");

            Connection secondHolder = probe.openHolder();
            ConflictProbe.execute(
                    secondHolder,
                    "UPDATE work_role_selections SET updated_at = updated_at WHERE user_id = " + member.userId());
            Future<WaitWindow.Timed<HttpResponse<String>>> writing = probe.asyncTimed(() -> fixture.send(
                    "PUT", "/api/me/work-role", memberToken, PortableFixture.json(Map.of("roleId", first))));
            probe.awaitWaiting();
            WaitWindow.Timed<HttpResponse<String>> writeResult = writing.get(15, TimeUnit.SECONDS);
            probe.release(secondHolder);

            WaitWindow.expectWrite("PgRowLockWaitIT.nextWriteOnTheSameConnection", writeResult.millis());
            PortableFixture.assertProblem(writeResult.value(), 409, "ROLE_BUSY");
        }
    }
}
