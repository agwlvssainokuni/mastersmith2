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

import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 一意の判定の大文字と小文字の扱い（BR2.2）。期待は、H2 と PostgreSQL の両方で実測した表（{@code code-summary.md} の Step 2 の 3）:
 * 大文字と小文字だけが違うロール・グループの名前は、後の作成が 409 の重なり。メールアドレスは小文字にそろえて照らす（ログインは大文字の
 * メールアドレスでも 200、登録済みのメールアドレスの招待は大文字でも 409）。
 */
@InternalDbPostgresIT
class PgCaseSensitivityIT {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static SmtpTestServer receiver;

    private static PgApp app;

    private static PortableFixture fixture;

    @BeforeAll
    static void start() {
        receiver = SmtpTestServer.builder().start();
        app = PgApp.startWithMail(receiver.port());
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        try {
            app.close();
        } finally {
            receiver.close();
        }
    }

    private static String suffix() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 先に作る名前と、後に作る名前（大文字と小文字だけが違う、または同じ）。 */
    private static String[][] pairs(String h) {
        return new String[][] {
            {"Alpha-" + h, "alpha-" + h},
            {"BETA-" + h, "beta-" + h},
            {"gamma-" + h, "GAMMA-" + h},
            {"Delta-" + h, "Delta-" + h}
        };
    }

    @Test
    @DisplayName("role names that differ only in case are 409 ROLE_NAME_DUPLICATE for the second one")
    void roleNames() {
        for (String[] pair : pairs(suffix())) {
            HttpResponse<String> first =
                    fixture.adminCall("POST", "/api/admin/roles", PortableFixture.json(Map.of("name", pair[0])));
            HttpResponse<String> second =
                    fixture.adminCall("POST", "/api/admin/roles", PortableFixture.json(Map.of("name", pair[1])));

            assertThat(first.statusCode()).isEqualTo(201);
            PortableFixture.assertProblem(second, 409, "ROLE_NAME_DUPLICATE");
        }
    }

    @Test
    @DisplayName("group names that differ only in case are 409 GROUP_NAME_DUPLICATE for the second one")
    void groupNames() {
        for (String[] pair : pairs(suffix())) {
            HttpResponse<String> first =
                    fixture.adminCall("POST", "/api/admin/groups", PortableFixture.json(Map.of("name", pair[0])));
            HttpResponse<String> second =
                    fixture.adminCall("POST", "/api/admin/groups", PortableFixture.json(Map.of("name", pair[1])));

            assertThat(first.statusCode()).isEqualTo(201);
            PortableFixture.assertProblem(second, 409, "GROUP_NAME_DUPLICATE");
        }
    }

    @Test
    @DisplayName(
            "an upper case email logs in with 200, and inviting a registered email in upper case is 409 INVITATION_EMAIL_REGISTERED")
    void emailAddresses() {
        PortableFixture.Account member = fixture.createUser(false);
        String upper = member.email().toUpperCase(Locale.ROOT);

        HttpResponse<String> login = fixture.loginResponse(upper, member.password());
        HttpResponse<String> invite = fixture.adminCall(
                "POST", "/api/admin/invitations", PortableFixture.json(Map.of("email", upper, "language", "ja")));

        assertThat(login.statusCode()).isEqualTo(200);
        PortableFixture.assertProblem(invite, 409, "INVITATION_EMAIL_REGISTERED");
    }
}
