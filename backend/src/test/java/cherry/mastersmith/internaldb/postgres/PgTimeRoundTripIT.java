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

import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PgClockConfig;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 時刻（BR7.1・BR7.2）。専用のクラス（クラスごとのアプリ。NFR 設計 DD-3）で、JVM の既定の時差を変えてからアプリを起動し、終わりに元へ戻す
 * （戻せたことを {@code @AfterAll} で確かめる）。(1) 書いた時点と読み出した時点が、JVM の時差（UTC・Asia/Tokyo・America/New_York）と PostgreSQL のセッションの
 * {@code TimeZone} の組によらず同じ（夏時間の境の時刻と、マイクロ秒の端数を含む）。(2) 注入した時計（{@code MutableClock}）を期限の直前・ちょうど・直後に進めて、
 * リフレッシュトークン・招待の有効期限とログインのロックの解除が、H2 の参照のテスト（{@code TokenApiIT}・{@code RegistrationApiIT}・{@code LoginApiIT}）と同じ結果になる
 * （実時間の {@code sleep} は使わない）。
 */
@InternalDbPostgresIT
class PgTimeRoundTripIT {

    private static final String PASSWORD = "登録用のテストパスワード-01";

    private static final List<String> JVM_ZONES = List.of("UTC", "Asia/Tokyo", "America/New_York");

    private static final List<String> SESSION_ZONES = List.of("UTC", "Asia/Kolkata", "America/New_York");

    /** 夏時間の境（アメリカ東部）の前後と、ふつうの時刻（マイクロ秒の端数つき）。 */
    private static final List<Instant> INSTANTS = List.of(
            Instant.parse("2026-03-08T06:59:59.999999Z"),
            Instant.parse("2026-03-08T07:30:00Z"),
            Instant.parse("2026-11-01T05:30:00Z"),
            Instant.parse("2026-11-01T06:30:00Z"),
            Instant.parse("2026-09-22T00:00:00.123456Z"));

    private static TimeZone original;

    private static PgApp clockApp;

    private static PortableFixture fixture;

    private static MutableClock clock;

    @BeforeAll
    static void start() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        clockApp = PgApp.startWithSources(new Class<?>[] {PgClockConfig.class});
        fixture = new PortableFixture(clockApp);
        clock = clockApp.context().getBean(MutableClock.class);
    }

    @AfterAll
    static void stop() {
        try {
            if (clockApp != null) {
                clockApp.close();
            }
        } finally {
            TimeZone.setDefault(original);
        }
        assertThat(TimeZone.getDefault().getID())
                .as("the JVM default time zone is restored after the class")
                .isEqualTo(original.getID());
    }

    private static String completeBody(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("displayName", "時刻の確かめ");
        body.put("password", PASSWORD);
        body.put("passwordConfirmation", PASSWORD);
        body.put("language", "ja");
        body.put("theme", "system");
        body.put("fontSize", "md");
        return PortableFixture.json(body);
    }

    @Test
    @DisplayName(
            "a written instant is read back as the same instant for every JVM zone and PostgreSQL session zone, including daylight saving boundaries")
    void instantsRoundTripAcrossZones() {
        TimeZone before = TimeZone.getDefault();
        try {
            for (String jvmZone : JVM_ZONES) {
                TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
                try (PgApp app = PgApp.start()) {
                    PortableFixture zoned = new PortableFixture(app);
                    DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
                    JdbcTemplate jdbc = app.context().getBean(JdbcTemplate.class);
                    TransactionTemplate tx =
                            new TransactionTemplate(app.context().getBean(PlatformTransactionManager.class));
                    long adminId = zoned.adminUserId();
                    for (String sessionZone : SESSION_ZONES) {
                        for (Instant instant : INSTANTS) {
                            Instant[] read = new Instant[2];
                            tx.execute(status -> {
                                jdbc.execute("SET LOCAL TimeZone = '" + sessionZone + "'");
                                previews.place(
                                        UUID.randomUUID(),
                                        "version: 2\n".getBytes(StandardCharsets.UTF_8),
                                        "a".repeat(64),
                                        DslSource.PASTE,
                                        adminId,
                                        instant);
                                DslPreviewRef ref = previews.findRef().orElseThrow();
                                read[0] = ref.placedAt();
                                String text = jdbc.queryForObject(
                                        "SELECT to_char(placed_at AT TIME ZONE 'UTC',"
                                                + " 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') FROM dsl_previews",
                                        String.class);
                                read[1] = Instant.parse(text);
                                return null;
                            });
                            assertThat(read[0])
                                    .as("read by the application: JVM=%s session=%s", jvmZone, sessionZone)
                                    .isEqualTo(instant);
                            assertThat(read[1])
                                    .as("read by SQL: JVM=%s session=%s", jvmZone, sessionZone)
                                    .isEqualTo(instant);
                        }
                    }
                }
            }
        } finally {
            TimeZone.setDefault(before);
        }
    }

    @Test
    @DisplayName(
            "a refresh token is usable one second before 24 hours and a rotated token is refused exactly at its expiry")
    void refreshTokenExpiry() {
        clock.set(PgClockConfig.START);
        PortableFixture.Account member = fixture.createUser(false);
        String cookie = PortableFixture.cookieValue(fixture.loginResponse(member.email(), member.password()));

        clock.advance(Duration.ofHours(24).minusSeconds(1));
        var justBefore = fixture.refresh(cookie, fixture.origin());
        String rotated = PortableFixture.cookieValue(justBefore);
        clock.advance(Duration.ofHours(24));

        assertThat(justBefore.statusCode()).isEqualTo(200);
        PortableFixture.assertProblem(fixture.refresh(rotated, fixture.origin()), 401, "REFRESH_FAILED");
    }

    @Test
    @DisplayName("an invitation passes one microsecond before its expiry and is refused exactly at and after it")
    void invitationExpiryBoundary() {
        clock.set(PgClockConfig.START);
        long adminId = fixture.adminUserId();
        Instant expiresAt = PgClockConfig.START.plus(Duration.ofHours(24));
        PortableFixture.Invitation before = fixture.insertInvitation(adminId, expiresAt);
        PortableFixture.Invitation at = fixture.insertInvitation(adminId, expiresAt);
        PortableFixture.Invitation after = fixture.insertInvitation(adminId, expiresAt);

        clock.set(expiresAt.minusNanos(1000));
        assertThat(verify(before.token())).isEqualTo(200);
        assertThat(complete(before.token())).isEqualTo(204);
        clock.set(expiresAt);
        assertThat(verify(at.token())).isEqualTo(404);
        assertThat(complete(at.token())).isEqualTo(404);
        clock.advance(Duration.ofNanos(1000));
        assertThat(complete(after.token())).isEqualTo(404);
    }

    private static int verify(String token) {
        return fixture.send("POST", "/api/registration/verify", null, PortableFixture.json(Map.of("token", token)))
                .statusCode();
    }

    private static int complete(String token) {
        return fixture.send("POST", "/api/registration/complete", null, completeBody(token))
                .statusCode();
    }

    @Test
    @DisplayName("a locked login is refused until the lock duration has passed and then succeeds again")
    void loginLockIsReleasedAfterTheDuration() {
        clock.set(PgClockConfig.START);
        PortableFixture.Account member = fixture.createUser(false);
        for (int i = 0; i < 5; i++) {
            fixture.loginResponse(member.email(), "wrong-" + member.password());
        }
        PortableFixture.assertProblem(
                fixture.loginResponse(member.email(), member.password()), 401, "AUTHENTICATION_FAILED");

        clock.advance(Duration.ofMinutes(30));

        assertThat(fixture.loginResponse(member.email(), member.password()).statusCode())
                .isEqualTo(200);
    }
}
