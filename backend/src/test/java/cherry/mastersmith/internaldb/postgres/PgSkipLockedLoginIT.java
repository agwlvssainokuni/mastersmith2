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
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ログインの状態の {@code SKIP LOCKED} の経路（BR3.4）。存在しないメールアドレスのログインはダミーの行（8 つ）のうち排他を持たれていない行を待たずに
 * 選ぶ。8 つのうち 7 つを持たれても 401 {@code AUTHENTICATION_FAILED} で短時間に返り、8 つすべてを持たれると待つ行を 1 つ選んでヒントの 3 秒で切れる
 * （500 {@code INTERNAL_ERROR}。H2 も同じ。Step 2 の実測）。
 */
@InternalDbPostgresIT
class PgSkipLockedLoginIT {

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

    private static HttpResponse<String> unknownLogin() {
        return fixture.loginResponse("pg-nobody-" + UUID.randomUUID() + "@example.com", "pw-" + UUID.randomUUID());
    }

    @Test
    @DisplayName(
            "with seven of the eight dummy rows held, an unknown login is 401 AUTHENTICATION_FAILED without waiting")
    void sevenHeldDoNotWait() throws Exception {
        Connection holder = probe.openHolder();
        ConflictProbe.execute(
                holder,
                "SELECT subject_id FROM login_attempt_states WHERE subject_id IN (-1, -2, -3, -4, -5, -6, -7) FOR UPDATE");

        WaitWindow.Timed<HttpResponse<String>> result = WaitWindow.measure(PgSkipLockedLoginIT::unknownLogin);

        System.out.println("INTERNAL_DB_PG_WAIT PgSkipLockedLoginIT.sevenHeldDoNotWait waitMs=" + result.millis());
        PortableFixture.assertProblem(result.value(), 401, "AUTHENTICATION_FAILED");
        assertThat(result.millis()).as("it does not wait for a held row").isLessThan(1_500L);
    }

    @Test
    @DisplayName(
            "with all eight dummy rows held, an unknown login waits within [3s, 4s) and ends as 500 INTERNAL_ERROR")
    void allHeldWaitForTheHint() throws Exception {
        Connection holder = probe.openHolder();
        ConflictProbe.execute(holder, "SELECT subject_id FROM login_attempt_states WHERE subject_id < 0 FOR UPDATE");

        Future<WaitWindow.Timed<HttpResponse<String>>> waiting = probe.asyncTimed(PgSkipLockedLoginIT::unknownLogin);
        probe.awaitWaiting();
        WaitWindow.Timed<HttpResponse<String>> result = waiting.get(15, TimeUnit.SECONDS);
        probe.release(holder);

        WaitWindow.expectRowLock("PgSkipLockedLoginIT.allHeldWaitForTheHint", result.millis());
        PortableFixture.assertProblem(result.value(), 500, "INTERNAL_ERROR");
        assertThat(fixture.loginResponse(app.adminEmail(), app.adminPassword()).statusCode())
                .as("the next request goes on")
                .isEqualTo(200);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM login_attempt_states WHERE subject_id < 0"))
                .isEqualTo(8L);
    }
}
