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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.businessdata.domain.ReferenceWritePolicy.RequiredDecision;
import cherry.mastersmith.businessdata.domain.RequiredReferenceState.BodyState;
import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 保存前の参照の段の判定の純粋な関数の単体テスト（Intent 261029-reference-lookup-cleanup の U2、BR1.3・BR1.4・BR2.5・BR3.2〜BR3.5・
 * BR4.4、functional-spec.md 4.3節の表）。値はすべて架空。
 */
class ReferenceWritePolicyTest {

    private static final ColumnValueCodec CODEC = new ColumnValueCodec();

    private static BoundValue read(ValueType type, String db, String text) {
        return CODEC.read(BusinessDataDslFixture.access(type, db), text).orElseThrow();
    }

    private static BoundValue text(String value) {
        return new BoundValue(ValueType.STRING, value);
    }

    private static ReferenceReadFailure failure(ReferenceFailureReason reason) {
        return new ReferenceReadFailure(reason, "57014", 0, "java.sql.SQLTimeoutException", false);
    }

    private static BusinessDataFieldError error(String column, BusinessDataFieldErrorReason reason) {
        return new BusinessDataFieldError(column, reason);
    }

    @Test
    @DisplayName("BR2.5: integers compare as numbers, decimals by magnitude and strings literally")
    void unchangedComparesTypedValues() {
        assertThat(ReferenceWritePolicy.unchanged(
                        read(ValueType.INTEGER, "int4", "007"), read(ValueType.INTEGER, "int4", "7")))
                .isTrue();
        assertThat(ReferenceWritePolicy.unchanged(
                        read(ValueType.DECIMAL, "numeric", "1.0"), read(ValueType.DECIMAL, "numeric", "1")))
                .isTrue();
        assertThat(ReferenceWritePolicy.unchanged(text("K1"), text("K1"))).isTrue();
        assertThat(ReferenceWritePolicy.unchanged(text("k1"), text("K1")))
                .as("大文字と小文字は違う")
                .isFalse();
        assertThat(ReferenceWritePolicy.unchanged(text("K1 "), text("K1")))
                .as("末尾の空白は違う")
                .isFalse();
    }

    @Test
    @DisplayName("BR2.5: an unreadable, truncated or NULL previous value is different")
    void unreadablePreviousIsDifferent() {
        assertThat(ReferenceWritePolicy.unchanged(null, text("K1"))).isFalse();
        assertThat(ReferenceWritePolicy.unchanged(text(null), text("K1"))).isFalse();
        assertThat(ReferenceWritePolicy.unchanged(text("K1"), text(null))).isFalse();
        assertThat(ReferenceWritePolicy.unchanged(new BoundValue(ValueType.DECIMAL, new BigDecimal("1")), text("1")))
                .as("型が違えば違う")
                .isFalse();
    }

    static Stream<Arguments> requiredGrid() {
        return Stream.of(
                Arguments.of(
                        ReferenceStatus.NO_PERMISSION, BodyState.VALUE, false, RequiredDecision.REFERENCE_NOT_FOUND),
                Arguments.of(
                        ReferenceStatus.NO_PERMISSION, BodyState.EMPTY, true, RequiredDecision.NOT_EDITABLE_NOT_NULL),
                Arguments.of(
                        ReferenceStatus.NO_PERMISSION, BodyState.EMPTY, false, RequiredDecision.NOT_EDITABLE_NOT_NULL),
                Arguments.of(ReferenceStatus.TOO_MANY, BodyState.VALUE, false, RequiredDecision.REFERENCE_NOT_FOUND),
                Arguments.of(ReferenceStatus.TOO_MANY, BodyState.EMPTY, true, RequiredDecision.NOT_EDITABLE_NOT_NULL),
                Arguments.of(ReferenceStatus.TOO_MANY, BodyState.EMPTY, false, RequiredDecision.NOT_EDITABLE_NOT_NULL),
                Arguments.of(ReferenceStatus.AVAILABLE, BodyState.VALUE, false, RequiredDecision.CHECK),
                Arguments.of(ReferenceStatus.AVAILABLE, BodyState.EMPTY, true, RequiredDecision.RETURN_HELD),
                Arguments.of(ReferenceStatus.AVAILABLE, BodyState.EMPTY, false, RequiredDecision.PROCEED));
    }

    @ParameterizedTest(name = "{0} with {1} (held={2}) -> {3}")
    @MethodSource("requiredGrid")
    @DisplayName("functional-spec 4.3: every cell of the required reference grid")
    void requiredGridCells(ReferenceStatus status, BodyState body, boolean held, RequiredDecision expected) {
        assertThat(ReferenceWritePolicy.decide(new RequiredReferenceState("c", status, body), held))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName(
            "the decisions map to REFERENCE_NOT_FOUND, NOT_EDITABLE_NOT_NULL with the column, the held errors or proceed")
    void decisionsMapToOutcomes() {
        List<BusinessDataFieldError> held = List.of(error("c", BusinessDataFieldErrorReason.REQUIRED));

        assertThat(ReferenceWritePolicy.ofDecision("c", RequiredDecision.REFERENCE_NOT_FOUND, List.of()))
                .isEqualTo(new ReferenceStepOutcome.Rejected(
                        List.of(error("c", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND))));
        assertThat(ReferenceWritePolicy.ofDecision("c", RequiredDecision.NOT_EDITABLE_NOT_NULL, List.of()))
                .isEqualTo(new ReferenceStepOutcome.Rejected(
                        List.of(error("c", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL))));
        assertThat(ReferenceWritePolicy.ofDecision("c", RequiredDecision.RETURN_HELD, held))
                .isEqualTo(new ReferenceStepOutcome.Invalid(held));
        assertThat(ReferenceWritePolicy.ofDecision("c", RequiredDecision.PROCEED, List.of()))
                .isEqualTo(new ReferenceStepOutcome.Proceed());
        assertThatThrownBy(() -> ReferenceWritePolicy.ofDecision("c", RequiredDecision.CHECK, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("check results map to proceed, REFERENCE_NOT_FOUND and a failure at the CHECK stage")
    void checkResults() {
        ReferenceReadFailure timeout = failure(ReferenceFailureReason.TIMEOUT);

        assertThat(ReferenceWritePolicy.ofCheck("c", new ReferenceCheckResult.Found()))
                .isEqualTo(new ReferenceStepOutcome.Proceed());
        assertThat(ReferenceWritePolicy.ofCheck("c", new ReferenceCheckResult.NotFoundOrDenied()))
                .isEqualTo(new ReferenceStepOutcome.Rejected(
                        List.of(error("c", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND))));
        assertThat(ReferenceWritePolicy.ofCheck("c", new ReferenceCheckResult.Failed(timeout)))
                .isEqualTo(new ReferenceStepOutcome.Failed(timeout, ReferenceStepOutcome.Stage.CHECK));
    }

    @Test
    @DisplayName("count states map to the reference status and a failure is refused")
    void countStates() {
        assertThat(ReferenceWritePolicy.statusOf(new ReferenceCountState.Available()))
                .isEqualTo(ReferenceStatus.AVAILABLE);
        assertThat(ReferenceWritePolicy.statusOf(new ReferenceCountState.TooMany()))
                .isEqualTo(ReferenceStatus.TOO_MANY);
        assertThat(ReferenceWritePolicy.statusOf(new ReferenceCountState.NoPermission()))
                .isEqualTo(ReferenceStatus.NO_PERMISSION);
        assertThatThrownBy(() -> ReferenceWritePolicy.statusOf(
                        new ReferenceCountState.Failed(failure(ReferenceFailureReason.UNAVAILABLE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("BR1.3: failure, then missing row, then 400, then 409 with every rejection in order, then proceed")
    void combineOrder() {
        ReferenceStepOutcome failed = new ReferenceStepOutcome.Failed(
                failure(ReferenceFailureReason.MISMATCH), ReferenceStepOutcome.Stage.CHECK);
        ReferenceStepOutcome missing = new ReferenceStepOutcome.RowMissing(WriteRejection.ROW_NOT_FOUND);
        ReferenceStepOutcome invalid =
                new ReferenceStepOutcome.Invalid(List.of(error("a", BusinessDataFieldErrorReason.REQUIRED)));
        ReferenceStepOutcome rejectedA = new ReferenceStepOutcome.Rejected(
                List.of(error("b", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND)));
        ReferenceStepOutcome rejectedB = new ReferenceStepOutcome.Rejected(
                List.of(error("c", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL)));
        ReferenceStepOutcome proceed = new ReferenceStepOutcome.Proceed();

        assertThat(ReferenceWritePolicy.combine(List.of(rejectedA, invalid, missing, failed)))
                .isEqualTo(failed);
        assertThat(ReferenceWritePolicy.combine(List.of(rejectedA, invalid, missing)))
                .isEqualTo(missing);
        assertThat(ReferenceWritePolicy.combine(List.of(rejectedA, invalid, proceed)))
                .isEqualTo(invalid);
        assertThat(ReferenceWritePolicy.combine(List.of(rejectedA, proceed, rejectedB)))
                .isEqualTo(new ReferenceStepOutcome.Rejected(List.of(
                        error("b", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND),
                        error("c", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL))));
        assertThat(ReferenceWritePolicy.combine(List.of(proceed, proceed))).isEqualTo(proceed);
        assertThat(ReferenceWritePolicy.combine(List.of())).isEqualTo(proceed);
    }

    @Test
    @DisplayName("BR1.4: outcomes map to the existing rejections and the field errors without new codes")
    void rejectionsAndFieldErrors() {
        List<BusinessDataFieldError> errors = List.of(error("b", BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));

        assertThat(ReferenceWritePolicy.rejectionOf(new ReferenceStepOutcome.Invalid(errors)))
                .isEqualTo(WriteRejection.VALIDATION_FAILED);
        assertThat(ReferenceWritePolicy.rejectionOf(new ReferenceStepOutcome.Rejected(errors)))
                .isEqualTo(WriteRejection.REFERENCE_NOT_FOUND);
        assertThat(ReferenceWritePolicy.rejectionOf(new ReferenceStepOutcome.RowMissing(WriteRejection.ROW_NOT_UNIQUE)))
                .isEqualTo(WriteRejection.ROW_NOT_UNIQUE);
        assertThat(ReferenceWritePolicy.rejectionOf(ReferenceFailureReason.TIMEOUT))
                .isEqualTo(WriteRejection.TARGET_DB_TIMEOUT);
        assertThat(ReferenceWritePolicy.rejectionOf(ReferenceFailureReason.UNAVAILABLE))
                .isEqualTo(WriteRejection.TARGET_DB_UNAVAILABLE);
        assertThat(ReferenceWritePolicy.rejectionOf(ReferenceFailureReason.MISMATCH))
                .isEqualTo(WriteRejection.TARGET_DB_MISMATCH);
        assertThat(ReferenceWritePolicy.rejectionOf(new ReferenceStepOutcome.Failed(
                        ReferenceReadFailure.ofBudgetExceeded(), ReferenceStepOutcome.Stage.CHECK)))
                .isEqualTo(WriteRejection.TARGET_DB_TIMEOUT);
        assertThatThrownBy(() -> ReferenceWritePolicy.rejectionOf(new ReferenceStepOutcome.Proceed()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ReferenceWritePolicy.fieldErrorsOf(new ReferenceStepOutcome.Rejected(errors)))
                .isEqualTo(errors);
        assertThat(ReferenceWritePolicy.fieldErrorsOf(
                        new ReferenceStepOutcome.RowMissing(WriteRejection.ROW_NOT_FOUND)))
                .isEmpty();
        assertThat(ReferenceWritePolicy.fieldErrorsOf(new ReferenceStepOutcome.Proceed()))
                .isEmpty();
        assertThat(ReferenceWritePolicy.fieldErrorsOf(new ReferenceStepOutcome.Failed(
                        ReferenceReadFailure.ofBudgetExceeded(), ReferenceStepOutcome.Stage.BORROW)))
                .isEmpty();
    }

    @Test
    @DisplayName("every reject audit reason of the reference step is an existing audit reason, and 400 is not audited")
    void auditReasons() {
        assertThat(BusinessDataAuditReason.of(WriteRejection.REFERENCE_NOT_FOUND))
                .contains(BusinessDataAuditReason.BUSINESS_REFERENCE_NOT_FOUND);
        assertThat(BusinessDataAuditReason.of(WriteRejection.VALIDATION_FAILED)).isEmpty();
    }
}
