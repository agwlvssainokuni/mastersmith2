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
package cherry.mastersmith.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** グループの操作の監査の出来事（BR8.1〜BR8.5・BR8.7、契約 C10）の単体テスト。 */
class GroupAuditEventTest {

    private static final Instant NOW = Instant.parse("2026-10-08T05:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.60", "Mozilla/5.0", "trace-0060");

    private static final GroupAuditDetail NAME = new GroupAuditDetail.Name("営業部");

    @Test
    @DisplayName("a success carries the actor, the group, the detail and the request origin without a failure")
    void success() {
        GroupAuditEvent event = GroupAuditEvent.succeeded(GroupOperation.CREATE, 7L, 70L, null, NAME, NOW, ORIGIN);

        assertThat(event.operation()).isEqualTo(GroupOperation.CREATE);
        assertThat(event.actorUserId()).isEqualTo(7L);
        assertThat(event.targetGroupId()).isEqualTo(70L);
        assertThat(event.targetUserId()).isNull();
        assertThat(event.succeeded()).isTrue();
        assertThat(event.failure()).isNull();
        assertThat(event.detail()).isEqualTo(NAME);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.sourceIp()).isEqualTo("192.0.2.60");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.traceId()).isEqualTo("trace-0060");
    }

    @Test
    @DisplayName("a rejection carries the reason, and a duplicate name on creation has no target group")
    void rejection() {
        GroupAuditEvent duplicate = GroupAuditEvent.failed(
                GroupOperation.CREATE, 7L, null, null, GroupAuditFailure.GROUP_NAME_DUPLICATE, NAME, NOW, ORIGIN);
        GroupAuditEvent noChange = GroupAuditEvent.failed(
                GroupOperation.ADD_MEMBER,
                7L,
                70L,
                8L,
                GroupAuditFailure.NO_CHANGE,
                new GroupAuditDetail.Membership("営業部"),
                NOW,
                ORIGIN);

        assertThat(duplicate.succeeded()).isFalse();
        assertThat(duplicate.failure()).isEqualTo(GroupAuditFailure.GROUP_NAME_DUPLICATE);
        assertThat(duplicate.targetGroupId()).isNull();
        assertThat(noChange.targetUserId()).isEqualTo(8L);
        assertThat(noChange.detail()).isEqualTo(new GroupAuditDetail.Membership("営業部"));
    }

    @Test
    @DisplayName("a missing group or user keeps the requested ids and has no detail")
    void missingTargetsHaveNoDetail() {
        GroupAuditEvent groupMissing = GroupAuditEvent.failed(
                GroupOperation.DELETE, 7L, 999L, null, GroupAuditFailure.GROUP_NOT_FOUND, null, NOW, ORIGIN);
        GroupAuditEvent userMissing = GroupAuditEvent.failed(
                GroupOperation.ADD_MEMBER, 7L, 70L, -1L, GroupAuditFailure.USER_NOT_FOUND, null, NOW, ORIGIN);

        assertThat(groupMissing.targetGroupId()).isEqualTo(999L);
        assertThat(groupMissing.detail()).isNull();
        assertThat(userMissing.targetUserId()).isEqualTo(-1L);
        assertThat(userMissing.detail()).isNull();
        assertThatThrownBy(() -> GroupAuditEvent.failed(
                        GroupOperation.DELETE, 7L, 999L, null, GroupAuditFailure.GROUP_NOT_FOUND, NAME, NOW, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("inconsistent combinations of result, reason, targets and detail are refused")
    void inconsistentCombinations() {
        assertThatThrownBy(() -> new GroupAuditEvent(
                        GroupOperation.CREATE,
                        7L,
                        70L,
                        null,
                        true,
                        GroupAuditFailure.NO_CHANGE,
                        NAME,
                        NOW,
                        "192.0.2.60",
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupAuditEvent.succeeded(GroupOperation.RENAME, 7L, 70L, 8L, NAME, NOW, ORIGIN))
                .as("名前の変更は対象の利用者を持たない")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupAuditEvent.succeeded(
                        GroupOperation.REMOVE_MEMBER,
                        7L,
                        70L,
                        null,
                        new GroupAuditDetail.Membership("営業部"),
                        NOW,
                        ORIGIN))
                .as("メンバーの外しは対象の利用者を持つ")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupAuditEvent.succeeded(GroupOperation.DELETE, 7L, 70L, null, null, NOW, ORIGIN))
                .as("成功は detail を持つ")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GroupAuditEvent.failed(GroupOperation.DELETE, 7L, 70L, null, null, NAME, NOW, ORIGIN))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GroupAuditEvent.succeeded(null, 7L, 70L, null, NAME, NOW, ORIGIN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the event type has no email address, display name, password or token component")
    void noPersonalOrSecretComponents() {
        assertThat(Arrays.stream(GroupAuditEvent.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .map(name -> name.toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.contains("email")
                        || name.contains("displayname")
                        || name.contains("password")
                        || name.contains("token")
                        || name.contains("hash"));
    }
}
