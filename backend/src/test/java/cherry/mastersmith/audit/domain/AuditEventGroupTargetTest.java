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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ロール・グループを対象に持つ監査イベント（Intent 261004-role-menu の U3、契約 C10、BR8.4〜BR8.6・NFR5.5）の単体テスト。
 */
class AuditEventGroupTargetTest {

    /** サロゲートペアで表される文字（Java の文字列の長さは 2）。 */
    private static final String EMOJI = "😀";

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-08T01:00:00Z");

    private static AuditEvent groupEvent(String detail) {
        return AuditEvent.withRoleGroupTarget(
                OCCURRED_AT,
                AuditEventType.GROUP_MEMBER_ADDED,
                AuditResult.FAILURE,
                AuditFailureReason.NO_CHANGE,
                "192.0.2.30",
                "Mozilla/5.0",
                "trace-0030",
                31L,
                32L,
                null,
                33L,
                detail);
    }

    @Test
    @DisplayName("the factory fills the actor, the targets and the detail and leaves the other optional columns empty")
    void factoryFillsTheTargets() {
        AuditEvent event = groupEvent("{\"groupName\":\"営業部\"}");

        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.getEventType()).isEqualTo(AuditEventType.GROUP_MEMBER_ADDED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(event.getFailureReason()).isEqualTo(AuditFailureReason.NO_CHANGE);
        assertThat(event.getSourceIp()).isEqualTo("192.0.2.30");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.getTraceId()).isEqualTo("trace-0030");
        assertThat(event.getActorUserId()).isEqualTo(31L);
        assertThat(event.getTargetUserId()).isEqualTo(32L);
        assertThat(event.getTargetRoleId()).isNull();
        assertThat(event.getTargetGroupId()).isEqualTo(33L);
        assertThat(event.getDetail()).isEqualTo("{\"groupName\":\"営業部\"}");
        assertThat(event.getEnteredEmail()).isNull();
        assertThat(event.getRequestPath()).isNull();
        assertThat(event.getTargetInvitationId()).isNull();
        assertThat(event.getDslHash()).isNull();
    }

    @Test
    @DisplayName("a detail of exactly 16384 UTF-16 units including surrogate pairs is accepted, one more is refused")
    void detailLimitCountsUtf16Units() {
        String atLimit = EMOJI.repeat(AuditEvent.MAX_DETAIL_LENGTH / 2);
        String overLimit = atLimit + "a";

        assertThat(atLimit).hasSize(16_384);
        assertThat(groupEvent(atLimit).getDetail()).isEqualTo(atLimit);
        assertThatThrownBy(() -> groupEvent(overLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length=16385")
                .hasMessageNotContaining(EMOJI);
    }

    @Test
    @DisplayName("a detail is never truncated: an over-limit value made of surrogate pairs is refused as a whole")
    void overLimitIsNotTruncated() {
        String overBySurrogatePair = EMOJI.repeat(AuditEvent.MAX_DETAIL_LENGTH / 2 + 1);

        assertThatThrownBy(() -> groupEvent(overBySurrogatePair))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length=16386");
    }

    @Test
    @DisplayName("the detail and the role and group targets may be empty")
    void emptyDetailAndTargets() {
        AuditEvent event = AuditEvent.withRoleGroupTarget(
                OCCURRED_AT,
                AuditEventType.GROUP_CREATED,
                AuditResult.FAILURE,
                AuditFailureReason.GROUP_NAME_DUPLICATE,
                "192.0.2.31",
                null,
                null,
                31L,
                null,
                null,
                null,
                null);

        assertThat(event.getDetail()).isNull();
        assertThat(event.getTargetGroupId()).isNull();
        assertThat(event.getTargetUserId()).isNull();
        assertThat(event.toString()).contains("detailLength=null");
    }

    @Test
    @DisplayName("the string form shows the role and group targets and only the length of the detail")
    void stringFormShowsOnlyTheDetailLength() {
        AuditEvent event = groupEvent("{\"groupName\":\"営業部\"}");

        assertThat(event.toString())
                .contains("targetRoleId=null")
                .contains("targetGroupId=33")
                .contains("detailLength=19")
                .doesNotContain("営業部")
                .doesNotContain("groupName");
    }
}
