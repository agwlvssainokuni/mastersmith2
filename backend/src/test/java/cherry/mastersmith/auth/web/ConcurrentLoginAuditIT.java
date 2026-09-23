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
package cherry.mastersmith.auth.web;

import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.audit.testsupport.AuditWriteBarrierConfig;
import cherry.mastersmith.audit.testsupport.AuditWriteBarrierConfig.AuditWriteBarrier;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 同時の成功のログインで、監査の記録が接続の待ちの時間切れで欠けないことの結合テスト（F2 の再現と回帰。FR3.1〜FR3.4、NFR1）。
 *
 * <p>ログインは確定の後に同じスレッドで監査を記録し、そのとき業務の接続（1本目）を持ったまま、新しいトランザクションで
 * 2本目の接続を借りる。{@link AuditWriteBarrierConfig} で N 件の要求を監査の書き込みの直前にそろえ、同時の要求の数が
 * プールの上限以上なら2本目の待ちが必ず起きる状態を作る。プールの上限は {@code application.yaml} の設定
 * （{@code MASTERSMITH_DB_MAXIMUM_POOL_SIZE}）のままで起動する。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuditWriteBarrierConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ConcurrentLoginAuditIT {

    private static final String PASSWORD = "同時ログインのパスワード-1234";

    /** 監査の書き込みの失敗の ERROR のメッセージ（{@code AuditEventListener} の固定の文）。 */
    private static final String AUDIT_FAILURE_MESSAGE = "監査イベントの記録に失敗しました";

    /** 接続プールの待ちの時間切れの例外の文。 */
    private static final String POOL_TIMEOUT_MESSAGE = "Connection is not available";

    /**
     * 待ち合わせの上限。そろわなかったときもテストを止めないための上限で、HTTP の要求の時間切れ（30 秒）より短くし、
     * 失敗のときも応答と記録の結果を確かめられるようにする。
     */
    private static final Duration BARRIER_MAX_WAIT = Duration.ofSeconds(20);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuditWriteBarrier barrier;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        barrier.disarm();
    }

    @AfterEach
    void tearDown() {
        barrier.disarm();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private List<String> newUsers(int count) {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String email = "concurrent-login-" + UUID.randomUUID() + "@example.com";
            userAccountService.createUser(email, new Password(PASSWORD), false);
            emails.add(email);
        }
        return emails;
    }

    private List<Integer> loginConcurrently(List<String> emails) throws Exception {
        executor = Executors.newFixedThreadPool(emails.size());
        CountDownLatch ready = new CountDownLatch(emails.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String email : emails) {
            AuthApi api = new AuthApi(port);
            results.add(executor.submit(() -> {
                ready.countDown();
                start.await(30, TimeUnit.SECONDS);
                return api.login(email, PASSWORD).statusCode();
            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(90, TimeUnit.SECONDS));
        }
        return statuses;
    }

    private long loginSucceededRows(Set<String> emails) {
        return new AuditRows(jdbc)
                .all().stream()
                        .filter(row -> "LOGIN_SUCCEEDED".equals(row.eventType()))
                        .filter(row -> emails.contains(row.enteredEmail()))
                        .count();
    }

    private static long countRecords(List<Map<String, Object>> records, String text) {
        return records.stream()
                .filter(record -> String.valueOf(record.get("message")).contains(text)
                        || String.valueOf(record.get("exception")).contains(text))
                .count();
    }

    @ParameterizedTest(name = "{0} simultaneous logins")
    @ValueSource(ints = {10, 20})
    @DisplayName("simultaneous successful logins are all answered 200 and all audited without a pool timeout")
    void simultaneousLoginsAreAllAudited(int concurrency, CapturedOutput output) throws Exception {
        List<String> emails = newUsers(concurrency);
        barrier.arm(concurrency, BARRIER_MAX_WAIT);

        long startNanos = System.nanoTime();
        List<Integer> statuses = loginConcurrently(emails);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        boolean allArrived = barrier.allArrived();
        int arrived = barrier.arrivedCount();
        barrier.disarm();

        long auditRows = loginSucceededRows(new HashSet<>(emails));
        List<Map<String, Object>> records = JsonLogRecords.parse(output.getOut());
        long auditFailures = countRecords(records, AUDIT_FAILURE_MESSAGE);
        long poolTimeouts = countRecords(records, POOL_TIMEOUT_MESSAGE);
        String facts = String.format(
                "concurrency=%d, statuses=%s, arrivedAtBarrier=%d, allArrived=%s, auditRows=%d,"
                        + " auditFailures=%d, poolTimeoutRecords=%d, elapsedMs=%d",
                concurrency, statuses, arrived, allArrived, auditRows, auditFailures, poolTimeouts, elapsedMillis);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(allArrived)
                .as("すべての要求が監査の書き込みの直前でそろい、同時に2本目を借りに行く（%s）", facts)
                .isTrue();
        softly.assertThat(statuses).as("すべてのログインが成功する（%s）", facts).containsOnly(200);
        softly.assertThat(auditRows).as("ログインの成功の監査の行が要求の数だけある（%s）", facts).isEqualTo(concurrency);
        softly.assertThat(auditFailures).as("監査の記録の失敗が無い（%s）", facts).isZero();
        softly.assertThat(poolTimeouts).as("接続の待ちの時間切れが無い（%s）", facts).isZero();
        softly.assertAll();
    }
}
