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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.mail.service.SmtpMailSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 指標のバケットの境界の結合テスト（Intent 260928-quality-followup の FR4.1・FR4.2・NFR3）。
 *
 * <p>{@code application.yaml} の {@code management.metrics.distribution.slo} が、実際の要求と送信で記録された Timer に当たり、
 * 決めた境界だけのバケットが付くこと（既定のバケットが出ていないこと）を、Spring の文脈の {@link MeterRegistry} で確かめる。
 * 指標の窓口の公開の範囲（health だけ）は {@code cherry.mastersmith.config.ExposureIT} で確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
class HistogramBucketsIT {

    /** {@code http.server.requests} の境界（ミリ秒）。警報のしきい値 1000 ms を含む。 */
    private static final double[] HTTP_BOUNDARIES_MS = {100, 250, 500, 1000, 2000, 5000};

    /** {@code mastersmith.mail.send} の境界（ミリ秒）。送信の時間切れを含む 10000 ms まで。 */
    private static final double[] MAIL_BOUNDARIES_MS = {100, 250, 500, 1000, 2000, 5000, 10000};

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    MailSender mailSender;

    @Test
    @DisplayName("http.server.requests of the login API has exactly the six configured buckets")
    void httpServerRequestsBuckets() {
        // 存在しない利用者で誤ったログインを送る（監査とロックの状態はこのテストの内部DB にだけ残る）。
        int status = new AuthApi(port)
                .login("histogram-" + System.nanoTime() + "@example.test", "誤ったパスワード-0000")
                .statusCode();
        assertThat(status).isEqualTo(401);

        Timer timer = meterRegistry
                .find("http.server.requests")
                .tag("uri", "/api/auth/login")
                .timer();

        assertThat(timer).as("the login request is recorded").isNotNull();
        assertThat(timer.count()).isPositive();
        assertThat(boundariesInMillis(timer)).containsExactly(HTTP_BOUNDARIES_MS);
    }

    @Test
    @DisplayName("mastersmith.mail.send has exactly the seven configured buckets up to 10 seconds")
    void mailSendBuckets() {
        // テストの既定では SMTP の接続先が無いため、送らずに NOT_CONFIGURED で終わる（観測は記録される）。
        mailSender.send(new MailRequest("invitation", "ja", "histogram@example.test", Map.of()));

        Timer timer = meterRegistry.find(SmtpMailSender.OBSERVATION_NAME).timer();

        assertThat(timer).as("the send attempt is recorded").isNotNull();
        assertThat(timer.count()).isPositive();
        assertThat(boundariesInMillis(timer)).containsExactly(MAIL_BOUNDARIES_MS);
    }

    @Test
    @DisplayName("no other timer of http.server.requests carries the default percentile histogram")
    void noDefaultHistogram() {
        new AuthApi(port).login("histogram-other@example.test", "誤ったパスワード-0000");

        List<Timer> timers =
                List.copyOf(meterRegistry.find("http.server.requests").timers());

        assertThat(timers).isNotEmpty();
        assertThat(timers)
                .allSatisfy(timer -> assertThat(boundariesInMillis(timer)).containsExactly(HTTP_BOUNDARIES_MS));
    }

    private static double[] boundariesInMillis(Timer timer) {
        CountAtBucket[] buckets = timer.takeSnapshot().histogramCounts();
        return Arrays.stream(buckets)
                .mapToDouble(bucket -> bucket.bucket(TimeUnit.MILLISECONDS))
                .toArray();
    }
}
