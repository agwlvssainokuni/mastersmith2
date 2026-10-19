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
package cherry.mastersmith.businessdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 応答の code から監査の理由の値への写しの単体テスト（Intent 261016-business-data-views の U2、BR11.3・BR11.6）。 */
class BusinessDataAuditReasonTest {

    /** 監査に残す拒否（BR11.3）。 */
    private static final Set<WriteRejection> AUDITED = EnumSet.of(
            WriteRejection.ROW_OPERATION_NOT_ALLOWED,
            WriteRejection.ROW_NOT_FOUND,
            WriteRejection.ROW_NOT_UNIQUE,
            WriteRejection.DUPLICATE_VALUE,
            WriteRejection.REFERENCE_NOT_FOUND,
            WriteRejection.SAVE_REJECTED,
            WriteRejection.TARGET_DB_UNAVAILABLE,
            WriteRejection.TARGET_DB_TIMEOUT,
            WriteRejection.TARGET_DB_MISMATCH);

    @Test
    @DisplayName("every audited rejection has a reason and every reason fits in 32 characters")
    void auditedRejectionsHaveShortReasons() {
        for (WriteRejection rejection : AUDITED) {
            assertThat(BusinessDataAuditReason.of(rejection))
                    .as(rejection.name())
                    .isPresent();
        }
        assertThat(Arrays.stream(BusinessDataAuditReason.values()).map(Enum::name))
                .allSatisfy(name -> assertThat(name.length()).as(name).isLessThanOrEqualTo(32));
    }

    @Test
    @DisplayName("rejections that are not audited have no reason")
    void notAuditedRejections() {
        for (WriteRejection rejection : EnumSet.complementOf(EnumSet.copyOf(AUDITED))) {
            assertThat(BusinessDataAuditReason.of(rejection))
                    .as(rejection.name())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("the operation-not-allowed rejection uses the short audit name and the others map one to one")
    void mappingIsExplicit() {
        assertThat(BusinessDataAuditReason.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED))
                .contains(BusinessDataAuditReason.BUSINESS_ROW_OP_NOT_ALLOWED);
        assertThat(AUDITED.stream().map(r -> BusinessDataAuditReason.of(r).orElseThrow()))
                .doesNotHaveDuplicates()
                .hasSize(BusinessDataAuditReason.values().length);
    }

    @Test
    @DisplayName("only the timeout reason makes an UNKNOWN result")
    void unknownOnlyForTimeout() {
        for (BusinessDataAuditReason reason : BusinessDataAuditReason.values()) {
            assertThat(BusinessDataAuditResult.failureOf(reason))
                    .as(reason.name())
                    .isEqualTo(
                            reason == BusinessDataAuditReason.BUSINESS_TARGET_DB_TIMEOUT
                                    ? BusinessDataAuditResult.UNKNOWN
                                    : BusinessDataAuditResult.FAILURE);
        }
    }
}
