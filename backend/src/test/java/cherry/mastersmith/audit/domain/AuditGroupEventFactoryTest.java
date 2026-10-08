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

import cherry.mastersmith.group.domain.GroupAuditDetail;
import cherry.mastersmith.group.domain.GroupAuditEvent;
import cherry.mastersmith.group.domain.GroupAuditFailure;
import cherry.mastersmith.group.domain.GroupOperation;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** グループの操作の出来事の監査イベントへの写し方（BR8.1〜BR8.7、契約 C10、計画の D-11）の単体テスト。 */
class AuditGroupEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.90", "Mozilla/5.0", "trace-0090");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String detail) {
        return JsonMapper.shared().readValue(detail, Map.class);
    }

    @Test
    @DisplayName("each operation maps to its event type and each failure to its reason")
    void typesAndReasons() {
        assertThat(AuditEventFactory.groupEventTypeOf(GroupOperation.CREATE)).isEqualTo(AuditEventType.GROUP_CREATED);
        assertThat(AuditEventFactory.groupEventTypeOf(GroupOperation.RENAME)).isEqualTo(AuditEventType.GROUP_RENAMED);
        assertThat(AuditEventFactory.groupEventTypeOf(GroupOperation.DELETE)).isEqualTo(AuditEventType.GROUP_DELETED);
        assertThat(AuditEventFactory.groupEventTypeOf(GroupOperation.ADD_MEMBER))
                .isEqualTo(AuditEventType.GROUP_MEMBER_ADDED);
        assertThat(AuditEventFactory.groupEventTypeOf(GroupOperation.REMOVE_MEMBER))
                .isEqualTo(AuditEventType.GROUP_MEMBER_REMOVED);
        assertThat(AuditEventFactory.groupEventTypeOf(null)).isNull();
        assertThat(AuditEventFactory.groupFailureReasonOf(GroupAuditFailure.GROUP_NOT_FOUND))
                .isEqualTo(AuditFailureReason.GROUP_NOT_FOUND);
        assertThat(AuditEventFactory.groupFailureReasonOf(GroupAuditFailure.USER_NOT_FOUND))
                .isEqualTo(AuditFailureReason.USER_NOT_FOUND);
        assertThat(AuditEventFactory.groupFailureReasonOf(GroupAuditFailure.GROUP_NAME_DUPLICATE))
                .isEqualTo(AuditFailureReason.GROUP_NAME_DUPLICATE);
        assertThat(AuditEventFactory.groupFailureReasonOf(GroupAuditFailure.GROUP_IN_USE))
                .isEqualTo(AuditFailureReason.GROUP_IN_USE);
        assertThat(AuditEventFactory.groupFailureReasonOf(GroupAuditFailure.NO_CHANGE))
                .isEqualTo(AuditFailureReason.NO_CHANGE);
        assertThat(AuditEventFactory.groupFailureReasonOf(null)).isNull();
    }

    @Test
    @DisplayName("a member addition records the actor, the group, the user and the membership detail")
    void memberAddition() {
        AuditEvent event = AuditEventFactory.from(GroupAuditEvent.succeeded(
                GroupOperation.ADD_MEMBER, 1L, 70L, 80L, new GroupAuditDetail.Membership("営業部"), NOW, ORIGIN));

        assertThat(event.getEventType()).isEqualTo(AuditEventType.GROUP_MEMBER_ADDED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getActorUserId()).isEqualTo(1L);
        assertThat(event.getTargetGroupId()).isEqualTo(70L);
        assertThat(event.getTargetUserId()).isEqualTo(80L);
        assertThat(event.getTargetRoleId()).isNull();
        assertThat(event.getEnteredEmail()).isNull();
        assertThat(event.getTargetInvitationId()).isNull();
        assertThat(event.getSourceIp()).isEqualTo("192.0.2.90");
        assertThat(event.getTraceId()).isEqualTo("trace-0090");
        assertThat(json(event.getDetail())).isEqualTo(Map.of("groupName", "営業部"));
    }

    @Test
    @DisplayName("the four detail shapes produce only their decided keys")
    void detailKeys() {
        assertThat(json(AuditDetailJson.of(new GroupAuditDetail.Name("営業部")))).isEqualTo(Map.of("name", "営業部"));
        assertThat(json(AuditDetailJson.of(new GroupAuditDetail.Rename("Sales", "SALES"))))
                .isEqualTo(Map.of("before", "Sales", "after", "SALES"));
        assertThat(json(AuditDetailJson.of(new GroupAuditDetail.InUse("営業部", 3, 2))))
                .isEqualTo(Map.of("name", "営業部", "members", 3, "assignedRoles", 2));
        assertThat(AuditDetailJson.of(null)).isNull();
    }

    @Test
    @DisplayName("names with quotes and markup are escaped as JSON strings and read back unchanged")
    void escaping() {
        String name = "\"営業\" <b>&'部'";

        String detail = AuditDetailJson.of(new GroupAuditDetail.Name(name));

        assertThat(json(detail)).isEqualTo(Map.of("name", name));
        assertThat(detail).startsWith("{\"name\":\"\\\"");
    }

    @Test
    @DisplayName("a rejection for a missing group or user has a failure reason and no detail")
    void missingTargets() {
        AuditEvent groupMissing = AuditEventFactory.from(GroupAuditEvent.failed(
                GroupOperation.DELETE, 1L, 999L, null, GroupAuditFailure.GROUP_NOT_FOUND, null, NOW, ORIGIN));
        AuditEvent userMissing = AuditEventFactory.from(GroupAuditEvent.failed(
                GroupOperation.ADD_MEMBER, 1L, 70L, -1L, GroupAuditFailure.USER_NOT_FOUND, null, NOW, ORIGIN));
        AuditEvent duplicate = AuditEventFactory.from(GroupAuditEvent.failed(
                GroupOperation.CREATE,
                1L,
                null,
                null,
                GroupAuditFailure.GROUP_NAME_DUPLICATE,
                new GroupAuditDetail.Name("Sales"),
                NOW,
                ORIGIN));

        assertThat(groupMissing.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(groupMissing.getFailureReason()).isEqualTo(AuditFailureReason.GROUP_NOT_FOUND);
        assertThat(groupMissing.getTargetGroupId()).isEqualTo(999L);
        assertThat(groupMissing.getDetail()).isNull();
        assertThat(userMissing.getFailureReason()).isEqualTo(AuditFailureReason.USER_NOT_FOUND);
        assertThat(userMissing.getTargetUserId()).isEqualTo(-1L);
        assertThat(userMissing.getDetail()).isNull();
        assertThat(duplicate.getTargetGroupId()).isNull();
        assertThat(json(duplicate.getDetail())).isEqualTo(Map.of("name", "Sales"));
    }
}
