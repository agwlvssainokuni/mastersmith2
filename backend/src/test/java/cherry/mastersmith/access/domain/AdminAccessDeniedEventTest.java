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
package cherry.mastersmith.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.ClientInfo;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAccessDeniedEventTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static ClientInfo client(String userAgent) {
        return new ClientInfo("198.51.100.7", userAgent, "0123456789abcdef0123456789abcdef");
    }

    @Test
    @DisplayName("the event carries every field the audit log records")
    void everyFieldIsPresent() {
        AdminAccessDeniedEvent event = AdminAccessDeniedEvent.of(
                NOW, AccessDeniedReason.NOT_ADMIN, "member@example.com", "/api/admin/check", client("テスト用の利用者環境"));

        assertThat(event.eventType()).isEqualTo(AccessEventType.ACCESS_DENIED);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.result()).isEqualTo(AccessResult.FAILURE);
        assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.NOT_ADMIN);
        assertThat(event.enteredEmail()).isEqualTo("member@example.com");
        assertThat(event.sourceIp()).isEqualTo("198.51.100.7");
        assertThat(event.userAgent()).isEqualTo("テスト用の利用者環境");
        assertThat(event.requestPath()).isEqualTo("/api/admin/check");
        assertThat(event.traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    @DisplayName("the email and the user agent may be missing")
    void optionalFieldsMayBeMissing() {
        AdminAccessDeniedEvent event = AdminAccessDeniedEvent.of(
                NOW,
                AccessDeniedReason.TOKEN_MISSING,
                null,
                "/api/admin/check",
                new ClientInfo("198.51.100.7", null, null));

        assertThat(event.enteredEmail()).isNull();
        assertThat(event.userAgent()).isNull();
        assertThat(event.traceId()).isNull();
        assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_MISSING);
    }

    @Test
    @DisplayName("the user agent and the request path are cut to 512 characters")
    void longValuesAreCut() {
        String longAgent = "あ".repeat(600);
        String longPath = "/api/admin/" + "a".repeat(600);

        AdminAccessDeniedEvent event =
                AdminAccessDeniedEvent.of(NOW, AccessDeniedReason.NOT_ADMIN, null, longPath, client(longAgent));

        assertThat(event.userAgent()).hasSize(ClientInfo.MAX_USER_AGENT_LENGTH);
        assertThat(event.requestPath()).hasSize(AdminAccessDeniedEvent.MAX_REQUEST_PATH_LENGTH);
        assertThat(longPath).startsWith(event.requestPath());
    }

    @Test
    @DisplayName("the query part of the request path is not recorded")
    void queryIsRemoved() {
        AdminAccessDeniedEvent event = AdminAccessDeniedEvent.of(
                NOW, AccessDeniedReason.NOT_ADMIN, null, "/api/admin/check?token=abc&next=/x", client(null));

        assertThat(event.requestPath()).isEqualTo("/api/admin/check");
        assertThat(AdminAccessDeniedEvent.normalizeRequestPath(null)).isNull();
    }

    @Test
    @DisplayName("the event has no field that could hold a token or a password")
    void noSecretFields() {
        assertThat(Arrays.stream(AdminAccessDeniedEvent.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .map(name -> name.toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.contains("token")
                        || name.contains("password")
                        || name.contains("secret")
                        || name.contains("authorization")
                        || name.contains("hash"));
    }

    @Test
    @DisplayName("the text form hides the email address so that it never reaches the application log")
    void textFormHidesTheEmail() {
        AdminAccessDeniedEvent withEmail = AdminAccessDeniedEvent.of(
                NOW, AccessDeniedReason.NOT_ADMIN, "member@example.com", "/api/admin/check", client("agent"));
        AdminAccessDeniedEvent withoutEmail = AdminAccessDeniedEvent.of(
                NOW, AccessDeniedReason.TOKEN_MISSING, null, "/api/admin/check", client("agent"));

        assertThat(withEmail.toString())
                .doesNotContain("member@example.com")
                .contains("enteredEmail=***")
                .contains("NOT_ADMIN")
                .contains("/api/admin/check");
        assertThat(withoutEmail.toString()).contains("enteredEmail=null");
    }
}
