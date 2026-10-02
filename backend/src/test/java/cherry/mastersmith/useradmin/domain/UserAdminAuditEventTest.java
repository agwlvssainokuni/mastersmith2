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
package cherry.mastersmith.useradmin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 管理の操作の監査の出来事（C6、BR6.1・BR6.2）と、理由の写しの単体テスト。 */
class UserAdminAuditEventTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.7", "Agent/7", "trace-7");

    @Test
    @DisplayName("the event carries no email, name, search text, failure count or token")
    void noPersonalValues() {
        assertThat(Arrays.stream(UserAdminAuditEvent.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly(
                        "operation",
                        "actorUserId",
                        "targetUserId",
                        "succeeded",
                        "failure",
                        "occurredAt",
                        "sourceIp",
                        "userAgent",
                        "traceId");
        UserAdminAuditEvent event = UserAdminAuditEvent.failed(
                AdminOperation.REVOKE_ADMIN, 3, 99, UserAdminAuditFailure.LAST_ACTIVE_ADMIN, NOW, ORIGIN);
        assertThat(event.toString()).doesNotContain("@").doesNotContain("email").doesNotContain("displayName");
    }

    @Test
    @DisplayName("a success has no failure and a failure always has one")
    void successAndFailure() {
        UserAdminAuditEvent done = UserAdminAuditEvent.succeeded(AdminOperation.SUSPEND, 3, 4, NOW, ORIGIN);

        assertThat(done.succeeded()).isTrue();
        assertThat(done.failure()).isNull();
        assertThat(done.sourceIp()).isEqualTo("192.0.2.7");
        assertThat(done.userAgent()).isEqualTo("Agent/7");
        assertThat(done.traceId()).isEqualTo("trace-7");
        assertThatThrownBy(() -> new UserAdminAuditEvent(
                        AdminOperation.SUSPEND,
                        3,
                        4,
                        true,
                        UserAdminAuditFailure.NO_CHANGE,
                        NOW,
                        "192.0.2.7",
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserAdminAuditEvent(
                        AdminOperation.SUSPEND, 3, 4, false, null, NOW, "192.0.2.7", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAdminAuditEvent.failed(AdminOperation.SUSPEND, 3, 4, null, NOW, ORIGIN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the target user id is kept as requested, even when the user does not exist")
    void keepsRequestedTarget() {
        UserAdminAuditEvent event = UserAdminAuditEvent.failed(
                AdminOperation.GRANT_ADMIN, 3, -5, UserAdminAuditFailure.USER_NOT_FOUND, NOW, ORIGIN);

        assertThat(event.targetUserId()).isEqualTo(-5);
        assertThat(event.actorUserId()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(RejectionReason.class)
    @DisplayName("every rejection reason maps to the audit failure of the same name")
    void reasonsMapByName(RejectionReason reason) {
        assertThat(UserAdminAuditFailure.of(reason).name()).isEqualTo(reason.name());
    }
}
