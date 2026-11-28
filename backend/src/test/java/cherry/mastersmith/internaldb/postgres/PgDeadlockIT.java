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
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 行き詰まり（BR3.3）。ロールへのグループの割り当ての API は、ロールの行 → グループの行の順に排他を取る。保持側がグループの行を先に持ち、アプリが
 * 待ちに入ったのを {@code pg_stat_activity} で観測した直後に、保持側がロールの行を取りに行くと輪ができる。合否は「ちょうど一方が排他の失敗
 * （40P01）で終わり、もう一方は続けられる」。どちらが犠牲になるかは合否にしない（NFR 設計 DD-2 の入れ替え。計画の「この計画が決める点」）。
 * 待ち続けないこと（10 秒以内に終わる）と、{@code deadlock_timeout} が 1 秒であることも確かめる。
 */
@InternalDbPostgresIT
class PgDeadlockIT {

    private static PgApp app;

    private static PortableFixture fixture;

    private ConflictProbe probe;

    @BeforeAll
    static void start() {
        app = PgApp.start();
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
    @DisplayName("the deadlock_timeout is one second")
    void deadlockTimeoutIsOneSecond() throws Exception {
        try (Connection connection = app.database().connect();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SHOW deadlock_timeout")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("1s");
        }
    }

    @Test
    @DisplayName(
            "a role-group assignment that closes a lock cycle ends with exactly one side failing as a lock failure and the other side going on")
    void exactlyOneSideFails() throws Exception {
        long roleId = fixture.createRole(PortableFixture.uniqueName("dead"));
        long groupId = fixture.createGroup(PortableFixture.uniqueName("dead"));
        Connection holder = probe.openHolder();
        ConflictProbe.execute(holder, "SELECT group_id FROM groups WHERE group_id = " + groupId + " FOR UPDATE");
        long start = System.nanoTime();

        Future<HttpResponse<String>> assigning = probe.async(() -> fixture.adminCall(
                "POST",
                "/api/admin/roles/" + roleId + "/assignments",
                PortableFixture.json(Map.of("groupId", groupId))));
        probe.awaitWaiting();
        String holderState =
                ConflictProbe.tryExecute(holder, "SELECT role_id FROM roles WHERE role_id = " + roleId + " FOR UPDATE");
        HttpResponse<String> appResponse = assigning.get(15, TimeUnit.SECONDS);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("INTERNAL_DB_PG_DEADLOCK appStatus=" + appResponse.statusCode() + " holderSqlState="
                + holderState + " elapsedMs=" + elapsedMillis);

        assertThat(elapsedMillis)
                .as("the deadlock is detected and does not wait forever")
                .isLessThan(10_000L);
        boolean appFailed = appResponse.statusCode() == 409;
        boolean holderFailed = holderState != null;
        assertThat(appFailed ^ holderFailed)
                .as("exactly one side fails: %s, holderSqlState=%s", PortableFixture.describe(appResponse), holderState)
                .isTrue();
        if (appFailed) {
            PortableFixture.assertProblem(appResponse, 409, "GROUP_BUSY");
            assertThat(holderState).as("the holder goes on").isNull();
        } else {
            assertThat(appResponse.statusCode()).as("the app goes on").isEqualTo(204);
            assertThat(holderState).isEqualTo("40P01");
        }
    }
}
