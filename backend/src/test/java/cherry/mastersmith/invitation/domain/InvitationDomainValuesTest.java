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
package cherry.mastersmith.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 招待の値の型（使える設定・問題の種類・出来事・送信の結果・状態の対応）の単体テスト（BR1.4・BR4.4・BR8.6・BR9.3、NFR8.1）。 */
class InvitationDomainValuesTest {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.1", "agent", "trace");

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    @DisplayName("availability lists the base URL reason before the SMTP reason")
    void availability() {
        assertThat(InvitationAvailability.of(true, true)).isEqualTo(new InvitationAvailability(true, List.of()));
        assertThat(InvitationAvailability.of(false, true).unavailableReasons())
                .containsExactly(UnavailableReason.BASE_URL_NOT_CONFIGURED);
        assertThat(InvitationAvailability.of(true, false).unavailableReasons())
                .containsExactly(UnavailableReason.SMTP_NOT_CONFIGURED);
        assertThat(InvitationAvailability.of(false, false).unavailableReasons())
                .containsExactly(UnavailableReason.BASE_URL_NOT_CONFIGURED, UnavailableReason.SMTP_NOT_CONFIGURED);
        assertThatThrownBy(() -> new InvitationAvailability(true, List.of(UnavailableReason.SMTP_NOT_CONFIGURED)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvitationAvailability(false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the five problem types have fixed codes, statuses and non-blank ja and en texts")
    void problemTypes() {
        assertThat(InvitationProblemTypes.all())
                .extracting(type -> type.code() + ":" + type.status())
                .containsExactly(
                        "INVITATION_EMAIL_REGISTERED:409",
                        "INVITATION_ALREADY_PENDING:409",
                        "INVITATION_NOT_CONFIGURED:503",
                        "INVITATION_NOT_FOUND:404",
                        "REGISTRATION_LINK_INVALID:404");
        for (ProblemType type : InvitationProblemTypes.all()) {
            assertThat(type.title().ja()).isNotBlank();
            assertThat(type.title().en()).isNotBlank();
            assertThat(type.description().ja()).isNotBlank();
            assertThat(type.description().en()).isNotBlank();
        }
    }

    @Test
    @DisplayName("events carry only ids, reasons and the origin")
    void events() {
        InvitationIssuedEvent issued = InvitationIssuedEvent.of(3, 7, NOW, ORIGIN);
        assertThat(issued.invitationId()).isEqualTo(3);
        assertThat(issued.actorUserId()).isEqualTo(7);
        assertThat(issued.sourceIp()).isEqualTo("192.0.2.1");
        assertThat(InvitationResentEvent.of(3, 7, NOW, ORIGIN).traceId()).isEqualTo("trace");
        assertThat(InvitationCancelledEvent.of(3, 7, NOW, ORIGIN).userAgent()).isEqualTo("agent");
        assertThat(RegistrationCompletedEvent.of(3, 9, NOW, ORIGIN).userId()).isEqualTo(9);
        RegistrationFailedEvent failed =
                RegistrationFailedEvent.of(null, LinkRejection.INVITATION_NOT_FOUND, NOW, ORIGIN);
        assertThat(failed.invitationId()).isNull();
        assertThat(failed.toString()).doesNotContain("@").doesNotContain("token=");
    }

    @Test
    @DisplayName("a failed registration event requires the invitation id exactly when the invitation was found")
    void failedEventConsistency() {
        assertThatThrownBy(() -> RegistrationFailedEvent.of(3L, LinkRejection.INVITATION_NOT_FOUND, NOW, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistrationFailedEvent.of(null, LinkRejection.INVITATION_EXPIRED, NOW, ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RegistrationFailedEvent.of(3L, LinkRejection.EMAIL_ALREADY_REGISTERED, NOW, ORIGIN)
                        .invitationId())
                .isEqualTo(3L);
        assertThatThrownBy(() -> new InvitationIssuedEvent(1, 2, null, "ip", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the send result maps PENDING to FAILED for the API")
    void sendResult() {
        assertThat(SendResult.of(true)).isEqualTo(SendResult.SENT);
        assertThat(SendResult.of(false)).isEqualTo(SendResult.FAILED);
        assertThat(SendResult.SENT.apiValue()).isEqualTo("SENT");
        assertThat(SendResult.FAILED.apiValue()).isEqualTo("FAILED");
        assertThat(SendResult.PENDING.apiValue()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("the converters map every value both ways and reject unknown columns")
    void converters() {
        InvitationStateConverter state = new InvitationStateConverter();
        SendResultConverter result = new SendResultConverter();
        for (InvitationState value : InvitationState.values()) {
            assertThat(state.convertToEntityAttribute(state.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        for (SendResult value : SendResult.values()) {
            assertThat(result.convertToEntityAttribute(result.convertToDatabaseColumn(value)))
                    .isEqualTo(value);
        }
        assertThat(state.convertToDatabaseColumn(null)).isNull();
        assertThat(state.convertToEntityAttribute(null)).isNull();
        assertThat(result.convertToDatabaseColumn(null)).isNull();
        assertThat(result.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> state.convertToEntityAttribute("pending")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> result.convertToEntityAttribute("OK")).isInstanceOf(IllegalStateException.class);
    }
}
