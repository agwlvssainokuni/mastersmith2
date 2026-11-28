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

import cherry.mastersmith.common.persistence.RowLockFailures;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.internaldb.testsupport.ConflictProbe;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.internaldb.testsupport.WaitWindow;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 書き込みの待ち（BR3.1）。別の接続が確定せずに持つ行へアプリが書くと、接続の待ちの上限（{@code lock_timeout} 2 秒）で、呼び出しの開始から 2 秒以上 3 秒未満で
 * 切れ、H2 と同じ排他の失敗の応答になる。作業ロールの保存は 409 {@code ROLE_BUSY}（{@code WorkRoleSwitchApiIT} の区分）、プレビューを置くは排他の失敗として
 * 分類できる例外（{@code DslPreviewPlaceConcurrencyIT} の確かめ）。表示の設定の保存は上限切れが 500 になる既存の挙動（H2 も同じ）のため使わない。
 * 待ちは {@code pg_stat_activity} で観測してから結果を受け取る（{@code sleep} に頼らない）。
 */
@InternalDbPostgresIT
class PgWriteWaitIT {

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

    private static String roleBody(long roleId) {
        return PortableFixture.json(Map.of("roleId", roleId));
    }

    @Test
    @DisplayName(
            "a work role save held by an uncommitted row ends within [2s, 3s) as 409 ROLE_BUSY, and succeeds after the release")
    void workRoleSaveIsBusy(CapturedOutput output) throws Exception {
        int logStart = output.getAll().length();
        PortableFixture.Account member = fixture.createUser(false);
        long first = fixture.createRole(PortableFixture.uniqueName("wait"));
        long second = fixture.createRole(PortableFixture.uniqueName("wait"));
        fixture.assignRoleToUser(first, member.userId());
        fixture.assignRoleToUser(second, member.userId());
        String token = fixture.login(member);
        assertThat(fixture.send("PUT", "/api/me/work-role", token, roleBody(second))
                        .statusCode())
                .as("the first save creates the row")
                .isEqualTo(204);
        Connection holder = probe.openHolder();
        ConflictProbe.execute(
                holder, "UPDATE work_role_selections SET updated_at = updated_at WHERE user_id = " + member.userId());

        Future<WaitWindow.Timed<HttpResponse<String>>> waiting =
                probe.asyncTimed(() -> fixture.send("PUT", "/api/me/work-role", token, roleBody(first)));
        probe.awaitWaiting();
        WaitWindow.Timed<HttpResponse<String>> result = waiting.get(15, TimeUnit.SECONDS);

        WaitWindow.expectWrite("PgWriteWaitIT.workRoleSaveIsBusy", result.millis());
        PortableFixture.assertProblem(result.value(), 409, "ROLE_BUSY");
        probe.release(holder);
        assertThat(fixture.send("PUT", "/api/me/work-role", token, roleBody(first))
                        .statusCode())
                .isEqualTo(204);
        assertThat(output.getAll().substring(logStart))
                .as("the user's email address is not in the application log")
                .doesNotContain(member.email());
    }

    @Test
    @DisplayName(
            "placing a preview held by an uncommitted row ends within [2s, 3s) as a lock failure and leaves the row alone")
    void previewPlaceIsALockFailure() throws Exception {
        String placed = fixture.placePreview(PortableFixture.minimalDsl("wait_t"));
        DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
        TransactionTemplate tx = new TransactionTemplate(app.context().getBean(PlatformTransactionManager.class));
        long adminId = fixture.adminUserId();
        Connection holder = probe.openHolder();
        ConflictProbe.execute(holder, "UPDATE dsl_previews SET dsl_hash = dsl_hash WHERE preview_slot = 1");

        Future<WaitWindow.Timed<Throwable>> waiting = probe.asyncTimed(() -> {
            try {
                tx.execute(status -> previews.place(
                        UUID.randomUUID(),
                        "version: 2\n".getBytes(StandardCharsets.UTF_8),
                        "f".repeat(64),
                        DslSource.PASTE,
                        adminId,
                        Instant.now()));
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
        probe.awaitWaiting();
        WaitWindow.Timed<Throwable> result = waiting.get(15, TimeUnit.SECONDS);
        probe.release(holder);

        WaitWindow.expectWrite("PgWriteWaitIT.previewPlaceIsALockFailure", result.millis());
        assertThat(result.value()).isNotNull();
        assertThat(RowLockFailures.isLockFailure(result.value()))
                .as("the exception class is %s", result.value().getClass().getSimpleName())
                .isTrue();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_previews WHERE preview_id = '" + placed + "'"))
                .isEqualTo(1);
    }
}
