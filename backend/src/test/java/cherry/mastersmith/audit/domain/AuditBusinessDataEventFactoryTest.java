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

import cherry.mastersmith.businessdata.domain.AuditDetailBuilder;
import cherry.mastersmith.businessdata.domain.BusinessDataAuditReason;
import cherry.mastersmith.businessdata.domain.BusinessDataAuditResult;
import cherry.mastersmith.businessdata.domain.BusinessDataChangedEvent;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 業務データの操作の出来事から監査イベントを作る単体テスト（Intent 261016-business-data-views の U2、契約 C5、BR11.2〜BR11.6）。 */
class AuditBusinessDataEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-20T01:02:03Z");

    private static final WriteOrigin ORIGIN =
            new WriteOrigin("192.0.2.40", "Mozilla/5.0", "/api/tables/rows", "trace-0040");

    private static BusinessDataChangedEvent event(WriteOperation operation, BusinessDataAuditReason reason) {
        return BusinessDataChangedEvent.of(
                3L,
                7L,
                "sample",
                "item",
                operation,
                reason,
                AuditDetailBuilder.build(Map.of("id", "101")),
                NOW,
                ORIGIN);
    }

    @Test
    @DisplayName("each operation has its own event type")
    void eventTypes() {
        assertThat(AuditEventFactory.from(event(WriteOperation.CREATE, null)).getEventType())
                .isEqualTo(AuditEventType.BUSINESS_ROW_CREATED);
        assertThat(AuditEventFactory.from(event(WriteOperation.UPDATE, null)).getEventType())
                .isEqualTo(AuditEventType.BUSINESS_ROW_UPDATED);
        assertThat(AuditEventFactory.from(event(WriteOperation.DELETE, null)).getEventType())
                .isEqualTo(AuditEventType.BUSINESS_ROW_DELETED);
    }

    @Test
    @DisplayName("a success keeps the actor, the three new columns, the key detail and the origin")
    void success() {
        AuditEvent audit = AuditEventFactory.from(event(WriteOperation.CREATE, null));

        assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(audit.getFailureReason()).isNull();
        assertThat(audit.getActorUserId()).isEqualTo(3L);
        assertThat(audit.getTargetSchema()).isEqualTo("sample");
        assertThat(audit.getTargetTable()).isEqualTo("item");
        assertThat(audit.getWorkRoleId()).isEqualTo(7L);
        assertThat(audit.getDetail()).isEqualTo("{\"key\":{\"id\":\"101\"},\"keyTruncated\":false}");
        assertThat(audit.getSourceIp()).isEqualTo("192.0.2.40");
        assertThat(audit.getRequestPath()).isEqualTo("/api/tables/rows");
        assertThat(audit.getTraceId()).isEqualTo("trace-0040");
        assertThat(audit.getEnteredEmail()).isNull();
        assertThat(audit.getTargetUserId()).isNull();
        assertThat(audit.getTargetRoleId()).isNull();
        assertThat(audit.getDslHash()).isNull();
    }

    @Test
    @DisplayName("all nine reasons map one to one and only the timeout is UNKNOWN")
    void reasonsAndUnknown() {
        for (BusinessDataAuditReason reason : BusinessDataAuditReason.values()) {
            AuditEvent audit = AuditEventFactory.from(event(WriteOperation.UPDATE, reason));

            assertThat(audit.getFailureReason().name()).as(reason.name()).isEqualTo(reason.name());
            assertThat(audit.getResult())
                    .as(reason.name())
                    .isEqualTo(
                            reason == BusinessDataAuditReason.BUSINESS_TARGET_DB_TIMEOUT
                                    ? AuditResult.UNKNOWN
                                    : AuditResult.FAILURE);
        }
        assertThat(Arrays.stream(BusinessDataAuditResult.values()).map(AuditEventFactory::businessDataResultOf))
                .containsExactly(AuditResult.SUCCESS, AuditResult.FAILURE, AuditResult.UNKNOWN);
    }

    @Test
    @DisplayName("a create without a primary key has no detail and the work role may be empty")
    void noKeyAndNoWorkRole() {
        AuditEvent audit = AuditEventFactory.from(BusinessDataChangedEvent.of(
                3L,
                null,
                "sample",
                "log",
                WriteOperation.CREATE,
                null,
                AuditDetailBuilder.build(Map.of()),
                NOW,
                ORIGIN));

        assertThat(audit.getDetail()).isNull();
        assertThat(audit.getWorkRoleId()).isNull();
    }

    @Test
    @DisplayName("the entity still refuses a detail over the limit and shows the new columns in its string form")
    void detailLimitAndStringForm() {
        assertThatThrownBy(() -> AuditEvent.forBusinessData(
                        NOW,
                        AuditEventType.BUSINESS_ROW_DELETED,
                        AuditResult.SUCCESS,
                        null,
                        "x",
                        null,
                        null,
                        null,
                        1L,
                        "s",
                        "t",
                        null,
                        "x".repeat(AuditEvent.MAX_DETAIL_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        String text = AuditEventFactory.from(event(WriteOperation.DELETE, null)).toString();
        assertThat(text)
                .contains("targetSchema=sample", "targetTable=item", "workRoleId=7")
                .doesNotContain("101");
    }
}
