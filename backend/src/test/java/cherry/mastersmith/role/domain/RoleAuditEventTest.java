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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ロールの操作の監査の出来事（BR11.1〜BR11.4・BR11.8、契約 C10）の単体テスト。 */
class RoleAuditEventTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.40", "Mozilla/5.0", "trace-0040");

    @Test
    @DisplayName("a success carries the actor, the role, the detail and the origin, and no user or group in B4")
    void success() {
        RoleAuditEvent event =
                RoleAuditEvent.succeeded(RoleOperation.CREATE, 1L, 50L, new RoleAuditDetail.Name("営業"), NOW, ORIGIN);

        assertThat(event.succeeded()).isTrue();
        assertThat(event.failure()).isNull();
        assertThat(event.targetRoleId()).isEqualTo(50L);
        assertThat(event.targetUserId()).isNull();
        assertThat(event.targetGroupId()).isNull();
        assertThat(event.sourceIp()).isEqualTo("192.0.2.40");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.traceId()).isEqualTo("trace-0040");
    }

    @Test
    @DisplayName("a rejection of a missing role has no detail, a name duplicate on create has no role id")
    void failures() {
        RoleAuditEvent missing = RoleAuditEvent.failed(
                RoleOperation.DELETE, 1L, 999L, RoleAuditFailure.ROLE_NOT_FOUND, null, NOW, ORIGIN);
        RoleAuditEvent duplicate = RoleAuditEvent.failed(
                RoleOperation.CREATE,
                1L,
                null,
                RoleAuditFailure.ROLE_NAME_DUPLICATE,
                new RoleAuditDetail.Name("営業"),
                NOW,
                ORIGIN);

        assertThat(missing.succeeded()).isFalse();
        assertThat(missing.detail()).isNull();
        assertThat(duplicate.targetRoleId()).isNull();
        assertThat(duplicate.failure()).isEqualTo(RoleAuditFailure.ROLE_NAME_DUPLICATE);
    }

    @Test
    @DisplayName("inconsistent combinations of result, reason, target and detail are refused")
    void invariants() {
        RoleAuditDetail detail = new RoleAuditDetail.Name("営業");
        assertThatThrownBy(() -> new RoleAuditEvent(
                        RoleOperation.CREATE,
                        1L,
                        50L,
                        null,
                        null,
                        true,
                        RoleAuditFailure.NO_CHANGE,
                        detail,
                        NOW,
                        "192.0.2.40",
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleAuditEvent.succeeded(RoleOperation.CREATE, 1L, 50L, null, NOW, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleAuditEvent(
                        RoleOperation.CREATE, 1L, null, null, null, true, null, detail, NOW, "192.0.2.40", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleAuditEvent.failed(
                        RoleOperation.RENAME, 1L, 9L, RoleAuditFailure.ROLE_NOT_FOUND, detail, NOW, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleAuditEvent.failed(
                        RoleOperation.RENAME, 1L, 9L, RoleAuditFailure.NO_CHANGE, null, NOW, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the event and its detail hold no e-mail address, display name or secret by their types")
    void noPersonalValues() {
        assertThat(RoleAuditEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("email", "displayName", "password", "token");
    }

    @Test
    @DisplayName("an assignment and a work role switch carry the target user or group with the role")
    void targetsOfB5() {
        RoleAuditEvent toGroup = RoleAuditEvent.succeeded(
                RoleOperation.ASSIGN, 1L, 50L, null, 70L, new RoleAuditDetail.Assignment("営業", "第一営業部"), NOW, ORIGIN);
        RoleAuditEvent userMissing = RoleAuditEvent.failed(
                RoleOperation.ASSIGN,
                1L,
                50L,
                999L,
                null,
                RoleAuditFailure.USER_NOT_FOUND,
                new RoleAuditDetail.Assignment("営業", null),
                NOW,
                ORIGIN);
        RoleAuditEvent switched = RoleAuditEvent.succeeded(
                RoleOperation.SWITCH_WORK_ROLE,
                8L,
                50L,
                8L,
                null,
                new RoleAuditDetail.WorkRoleSwitch(null, null, "営業", null),
                NOW,
                ORIGIN);

        assertThat(toGroup.targetGroupId()).isEqualTo(70L);
        assertThat(toGroup.targetUserId()).isNull();
        assertThat(userMissing.targetUserId()).isEqualTo(999L);
        assertThat(userMissing.failure()).isEqualTo(RoleAuditFailure.USER_NOT_FOUND);
        assertThat(switched.actorUserId()).isEqualTo(switched.targetUserId());
    }

    @Test
    @DisplayName("a switch to a role outside the assignments has no detail and keeps the requested role id")
    void notAssigned() {
        RoleAuditEvent event = RoleAuditEvent.failed(
                RoleOperation.SWITCH_WORK_ROLE,
                8L,
                12345L,
                8L,
                null,
                RoleAuditFailure.ROLE_NOT_ASSIGNED,
                null,
                NOW,
                ORIGIN);

        assertThat(event.targetRoleId()).isEqualTo(12345L);
        assertThat(event.detail()).isNull();
        assertThatThrownBy(() -> RoleAuditEvent.failed(
                        RoleOperation.SWITCH_WORK_ROLE,
                        8L,
                        12345L,
                        8L,
                        null,
                        RoleAuditFailure.ROLE_NOT_ASSIGNED,
                        new RoleAuditDetail.WorkRoleSwitch(null, null, "営業", null),
                        NOW,
                        ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
