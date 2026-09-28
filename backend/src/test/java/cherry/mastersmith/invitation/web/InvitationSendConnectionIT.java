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
package cherry.mastersmith.invitation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationApps;
import cherry.mastersmith.mail.testsupport.SilentSmtpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 送信の間に内部DB の接続を持たないことの結合テスト（NFR5.1、ADR-009、{@code performance-design.md} 2.1 の結合テスト (1)・(2)）。
 * 応答しない受け手への送信の最中（受け手が接続を受け付けたことを上限の時間つきで待つ）に、接続プールの使用中の数が 0 で、一覧の API が
 * 送信の時間切れを待たずに応答することを確かめる。
 */
class InvitationSendConnectionIT {

    /** 送信の読み取りの時間切れ（テストの窓を広げるため 5 秒）。 */
    private static final String READ_TIMEOUT = "5000";

    @Test
    @DisplayName("while a send waits on a silent receiver no pool connection is active and the list responds at once")
    void noConnectionDuringSend(@TempDir Path dir) throws Exception {
        try (SilentSmtpServer silent = new SilentSmtpServer()) {
            Map<String, String> settings =
                    new java.util.LinkedHashMap<>(InvitationApps.mail(silent.port(), "http://localhost:8080"));
            settings.put("spring.mail.properties.mail.smtp.timeout", READ_TIMEOUT);
            try (ConfigurableApplicationContext context = InvitationApps.start(dir, settings)) {
                InvitationApi api = new InvitationApi(InvitationApps.port(context));
                String admin = InvitationApps.adminToken(context);
                HikariDataSource pool = context.getBean(HikariDataSource.class);

                CompletableFuture<HttpResponse<String>> invite = CompletableFuture.supplyAsync(
                        () -> api.invite(admin, "invitee-" + UUID.randomUUID() + "@example.com", "ja"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (silent.connections() == 0 && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertThat(silent.connections()).as("受け手が接続を受け付けた").isEqualTo(1);

                int active = pool.getHikariPoolMXBean().getActiveConnections();
                HttpResponse<String> list = api.list(admin, "");

                assertThat(active).as("送信の最中の使用中の接続").isZero();
                assertThat(list.statusCode()).isEqualTo(200);
                assertThat(invite).as("一覧は送信の時間切れを待たずに応答した").isNotDone();
                HttpResponse<String> created = invite.get(20, TimeUnit.SECONDS);
                assertThat(created.statusCode()).isEqualTo(201);
                assertThat(created.body()).contains("\"sendResult\":\"FAILED\"");
                assertThat(List.of(pool.getHikariPoolMXBean().getActiveConnections()))
                        .containsExactly(0);
            }
        }
    }
}
