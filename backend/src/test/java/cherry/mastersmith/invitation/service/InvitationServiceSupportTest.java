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
package cherry.mastersmith.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.invitation.domain.InvitationProblemTypes;
import cherry.mastersmith.invitation.domain.InvitationToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** トークンの発行と問題の種類の一覧の単体テスト（BR3.1・BR9.3）。 */
class InvitationServiceSupportTest {

    @Test
    @DisplayName("the issuer creates a new well-formed token each time")
    void issuer() {
        InvitationTokenIssuer issuer = new InvitationTokenIssuer();
        InvitationToken first = issuer.issue();
        InvitationToken second = issuer.issue();

        assertThat(InvitationToken.parse(first.value())).isPresent();
        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    @DisplayName("the catalog offers exactly the five invitation problem types")
    void catalog() {
        assertThat(new InvitationProblemTypeCatalog().problemTypes()).isEqualTo(InvitationProblemTypes.all());
        assertThat(new InvitationProblemTypeCatalog().problemTypes()).hasSize(5);
    }

    @Test
    @DisplayName("the operation names are INVITE and RESEND")
    void operations() {
        assertThat(InvitationOperation.values()).extracting(Enum::name).containsExactly("INVITE", "RESEND");
    }
}
