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
package cherry.mastersmith.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** ロールの操作の出来事の監査イベントへの写し方（BR11.1〜BR11.9、契約 C10、NFR5.1）の単体テスト。 */
class AuditRoleEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:30:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.91", "Mozilla/5.0", "trace-0091");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String detail) {
        return JsonMapper.shared().readValue(detail, Map.class);
    }

    @Test
    @DisplayName("each operation maps to its event type and each failure to its reason")
    void typesAndReasons() {
        assertThat(AuditEventFactory.roleEventTypeOf(RoleOperation.CREATE)).isEqualTo(AuditEventType.ROLE_CREATED);
        assertThat(AuditEventFactory.roleEventTypeOf(RoleOperation.RENAME)).isEqualTo(AuditEventType.ROLE_RENAMED);
        assertThat(AuditEventFactory.roleEventTypeOf(RoleOperation.DELETE)).isEqualTo(AuditEventType.ROLE_DELETED);
        assertThat(AuditEventFactory.roleEventTypeOf(RoleOperation.CHANGE_PERMISSIONS))
                .isEqualTo(AuditEventType.ROLE_PERMISSION_CHANGED);
        assertThat(AuditEventFactory.roleEventTypeOf(null)).isNull();
        assertThat(Arrays.stream(RoleAuditFailure.values()).map(AuditEventFactory::roleFailureReasonOf))
                .containsExactly(
                        AuditFailureReason.ROLE_NOT_FOUND,
                        AuditFailureReason.ROLE_NAME_DUPLICATE,
                        AuditFailureReason.ROLE_IN_USE,
                        AuditFailureReason.PERMISSION_TARGET_NOT_IN_DSL,
                        AuditFailureReason.DSL_NOT_APPLIED,
                        AuditFailureReason.NO_CHANGE);
        assertThat(AuditEventFactory.roleFailureReasonOf(null)).isNull();
    }

    @Test
    @DisplayName("the added type and reason names are at most 32 characters (BR11.9)")
    void namesFitTheColumns() {
        assertThat(Arrays.stream(AuditEventType.values())
                        .filter(type -> type.name().startsWith("ROLE_")))
                .hasSize(4)
                .allSatisfy(type -> assertThat(type.name().length()).isLessThanOrEqualTo(32));
        assertThat(AuditFailureReason.PERMISSION_TARGET_NOT_IN_DSL.name()).hasSizeLessThanOrEqualTo(32);
    }

    @Test
    @DisplayName("a rename records the actor, the role and the rename detail, and no user, group or e-mail")
    void rename() {
        AuditEvent event = AuditEventFactory.from(RoleAuditEvent.succeeded(
                RoleOperation.RENAME, 1L, 50L, new RoleAuditDetail.Rename("Sales", "営業"), NOW, ORIGIN));

        assertThat(event.getEventType()).isEqualTo(AuditEventType.ROLE_RENAMED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getActorUserId()).isEqualTo(1L);
        assertThat(event.getTargetRoleId()).isEqualTo(50L);
        assertThat(event.getTargetUserId()).isNull();
        assertThat(event.getTargetGroupId()).isNull();
        assertThat(event.getEnteredEmail()).isNull();
        assertThat(event.getSourceIp()).isEqualTo("192.0.2.91");
        assertThat(event.getTraceId()).isEqualTo("trace-0091");
        assertThat(json(event.getDetail())).isEqualTo(Map.of("before", "Sales", "after", "営業"));
    }

    @Test
    @DisplayName("a rejection of a missing role keeps the requested id and has no detail")
    void missingRole() {
        AuditEvent event = AuditEventFactory.from(RoleAuditEvent.failed(
                RoleOperation.DELETE, 1L, -1L, RoleAuditFailure.ROLE_NOT_FOUND, null, NOW, ORIGIN));

        assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(event.getFailureReason()).isEqualTo(AuditFailureReason.ROLE_NOT_FOUND);
        assertThat(event.getTargetRoleId()).isEqualTo(-1L);
        assertThat(event.getDetail()).isNull();
    }

    @Test
    @DisplayName("the result cannot be decided from the four role types alone")
    void resultComesFromTheEvent() {
        for (AuditEventType type : new AuditEventType[] {
            AuditEventType.ROLE_CREATED,
            AuditEventType.ROLE_RENAMED,
            AuditEventType.ROLE_DELETED,
            AuditEventType.ROLE_PERMISSION_CHANGED
        }) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditEventFactory.resultOf(type))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
