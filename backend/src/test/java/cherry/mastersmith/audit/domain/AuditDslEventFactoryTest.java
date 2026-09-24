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

import cherry.mastersmith.dslmanage.domain.DslOperationEvent;
import cherry.mastersmith.dslmanage.domain.DslOperationType;
import cherry.mastersmith.dslmanage.domain.DslSource;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** DSL の操作の出来事から監査イベントを作る（契約 C7、BR7.1・BR7.2）の単体テスト。 */
class AuditDslEventFactoryTest {

    private static final Instant AT = Instant.parse("2026-09-24T02:00:00Z");

    private static DslOperationEvent event(DslOperationType type, String rejectionKind) {
        return new DslOperationEvent(
                type, 7L, AT, "a".repeat(64), DslSource.UPLOAD, rejectionKind, "192.0.2.1", "x".repeat(600), "t-1");
    }

    @ParameterizedTest
    @EnumSource(DslOperationType.class)
    @DisplayName("every DSL event becomes an audit event of the same name with the DSL columns")
    void everyTypeIsMapped(DslOperationType type) {
        boolean rejected = type == DslOperationType.DSL_SUBMISSION_REJECTED;
        AuditEvent audit = AuditEventFactory.from(event(type, rejected ? "SYNTAX" : null));

        assertThat(audit.getEventType().name()).isEqualTo(type.name());
        assertThat(audit.getResult()).isEqualTo(rejected ? AuditResult.FAILURE : AuditResult.SUCCESS);
        assertThat(audit.getActorUserId()).isEqualTo(7L);
        assertThat(audit.getDslHash()).isEqualTo("a".repeat(64));
        assertThat(audit.getDslSource()).isEqualTo("UPLOAD");
        assertThat(audit.getRejectionKind()).isEqualTo(rejected ? "SYNTAX" : null);
        assertThat(audit.getOccurredAt()).isEqualTo(AT);
        assertThat(audit.getSourceIp()).isEqualTo("192.0.2.1");
        assertThat(audit.getUserAgent()).hasSize(AuditText.MAX_USER_AGENT_LENGTH);
        assertThat(audit.getTraceId()).isEqualTo("t-1");
        assertThat(audit.getEnteredEmail()).isNull();
        assertThat(audit.getRequestPath()).isNull();
        assertThat(audit.toString()).contains("actorUserId=7").contains("rejectionKind=");
    }

    @Test
    @DisplayName("an oversized submission has no hash and no source, and the rejection kind is required only there")
    void oversizedSubmissionAndRejectionKindRule() {
        AuditEvent audit = AuditEventFactory.from(new DslOperationEvent(
                DslOperationType.DSL_SUBMISSION_REJECTED, 7L, AT, null, null, "SIZE_LIMIT", "192.0.2.1", null, null));

        assertThat(audit.getDslHash()).isNull();
        assertThat(audit.getDslSource()).isNull();
        assertThat(audit.getRejectionKind()).isEqualTo("SIZE_LIMIT");
        assertThatThrownBy(() -> event(DslOperationType.DSL_APPLIED, "SYNTAX"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event(DslOperationType.DSL_SUBMISSION_REJECTED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
