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

import cherry.mastersmith.businessdata.domain.ReferenceWritePolicy.RequiredDecision;
import cherry.mastersmith.businessdata.domain.RequiredReferenceState.BodyState;
import java.math.BigDecimal;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 保存前の参照の段の判定の性質ベースのテスト（jqwik。Intent 261029-reference-lookup-cleanup の U2、NFR1.31、security-design.md
 * 6節）。失敗したときは jqwik が乱数の種を報告に出すので、{@code @Property(seed = …)} で再現する。
 */
class ReferenceWritePolicyPropertyTest {

    @Provide
    Arbitrary<BoundValue> values() {
        Arbitrary<BoundValue> strings =
                Arbitraries.strings().withChars("aAbB 1_").ofMaxLength(4).map(s -> new BoundValue(ValueType.STRING, s));
        Arbitrary<BoundValue> integers =
                Arbitraries.longs().between(-3, 3).map(l -> new BoundValue(ValueType.INTEGER, l));
        Arbitrary<BoundValue> decimals = Combinators.combine(
                        Arbitraries.integers().between(-20, 20),
                        Arbitraries.integers().between(0, 2))
                .as((unscaled, scale) -> new BoundValue(ValueType.DECIMAL, BigDecimal.valueOf(unscaled, scale)));
        Arbitrary<BoundValue> nulls =
                Arbitraries.of(ValueType.STRING, ValueType.INTEGER).map(t -> new BoundValue(t, null));
        return Arbitraries.oneOf(strings, integers, decimals, nulls);
    }

    @Provide
    Arbitrary<ReferenceStepOutcome> partials() {
        ReferenceReadFailure failure = new ReferenceReadFailure(
                ReferenceFailureReason.UNAVAILABLE, "08006", 0, "java.sql.SQLException", false);
        return Arbitraries.oneOf(
                Arbitraries.just(new ReferenceStepOutcome.Proceed()),
                Arbitraries.of("a", "b", "c")
                        .map(c -> new ReferenceStepOutcome.Rejected(List.of(
                                new BusinessDataFieldError(c, BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND)))),
                Arbitraries.of("a", "b", "c")
                        .map(c -> new ReferenceStepOutcome.Invalid(
                                List.of(new BusinessDataFieldError(c, BusinessDataFieldErrorReason.REQUIRED)))),
                Arbitraries.of(WriteRejection.ROW_NOT_FOUND, WriteRejection.ROW_NOT_UNIQUE)
                        .map(ReferenceStepOutcome.RowMissing::new),
                Arbitraries.of(ReferenceStepOutcome.Stage.class)
                        .map(stage -> new ReferenceStepOutcome.Failed(failure, stage)),
                Arbitraries.just(new ReferenceStepOutcome.Failed(
                        ReferenceReadFailure.ofBudgetExceeded(), ReferenceStepOutcome.Stage.CHECK)));
    }

    @Property
    @Label("the comparison is reflexive for non NULL values and symmetric for all values")
    void comparisonIsReflexiveAndSymmetric(@ForAll("values") BoundValue a, @ForAll("values") BoundValue b) {
        if (a.value() != null) {
            assertThat(ReferenceWritePolicy.unchanged(a, a)).isTrue();
        }
        assertThat(ReferenceWritePolicy.unchanged(a, b)).isEqualTo(ReferenceWritePolicy.unchanged(b, a));
    }

    @Property
    @Label("a value equal to the previous one is never checked and a different non NULL value is always checked")
    void sameValuesAreNotChecked(@ForAll("values") BoundValue previous, @ForAll("values") BoundValue body) {
        boolean checked = body.value() != null && !ReferenceWritePolicy.unchanged(previous, body);
        boolean equal = previous.value() != null
                && body.value() != null
                && previous.valueType() == body.valueType()
                && (previous.value() instanceof BigDecimal x && body.value() instanceof BigDecimal y
                        ? x.compareTo(y) == 0
                        : previous.value().equals(body.value()));

        assertThat(checked).isEqualTo(body.value() != null && !equal);
    }

    @Property
    @Label("an unreadable or too many required reference with a value is always rejected as REFERENCE_NOT_FOUND")
    void deadEndWithValueIsRejected(@ForAll("deadEnds") ReferenceStatus status, @ForAll boolean held) {
        RequiredDecision decision =
                ReferenceWritePolicy.decide(new RequiredReferenceState("c", status, BodyState.VALUE), held);

        assertThat(decision).isEqualTo(RequiredDecision.REFERENCE_NOT_FOUND);
        assertThat(ReferenceWritePolicy.rejectionOf(ReferenceWritePolicy.ofDecision("c", decision, List.of())))
                .isEqualTo(WriteRejection.REFERENCE_NOT_FOUND);
    }

    @Provide
    Arbitrary<ReferenceStatus> deadEnds() {
        return Arbitraries.of(ReferenceStatus.NO_PERMISSION, ReferenceStatus.TOO_MANY);
    }

    @Property
    @Label("adding read permission (NO_PERMISSION to AVAILABLE) never reduces the combinations that can be saved")
    void permissionNeverReducesSaves(@ForAll BodyState body, @ForAll boolean held, @ForAll boolean found) {
        boolean before = saves(ReferenceStatus.NO_PERMISSION, body, held, found);
        boolean after = saves(ReferenceStatus.AVAILABLE, body, held, found);

        assertThat(!before || after).isTrue();
    }

    private static boolean saves(ReferenceStatus status, BodyState body, boolean held, boolean found) {
        return switch (ReferenceWritePolicy.decide(new RequiredReferenceState("c", status, body), held)) {
            case CHECK -> found;
            case PROCEED -> true;
            case REFERENCE_NOT_FOUND, NOT_EDITABLE_NOT_NULL, RETURN_HELD -> false;
        };
    }

    @Provide
    Arbitrary<List<ReferenceStepOutcome>> partialLists() {
        return partials().list().ofMaxSize(6);
    }

    @Property
    @Label("a failure in any position wins over every rejection and maps to its own code")
    void failureWins(@ForAll("partialLists") List<ReferenceStepOutcome> partials) {
        ReferenceStepOutcome combined = ReferenceWritePolicy.combine(partials);
        ReferenceStepOutcome firstFailure = partials.stream()
                .filter(p -> p instanceof ReferenceStepOutcome.Failed)
                .findFirst()
                .orElse(null);
        ReferenceStepOutcome firstMissing = partials.stream()
                .filter(p -> p instanceof ReferenceStepOutcome.RowMissing)
                .findFirst()
                .orElse(null);
        boolean anyInvalid = partials.stream().anyMatch(p -> p instanceof ReferenceStepOutcome.Invalid);
        boolean anyRejected = partials.stream().anyMatch(p -> p instanceof ReferenceStepOutcome.Rejected);

        if (firstFailure != null) {
            assertThat(combined).isEqualTo(firstFailure);
            assertThat(ReferenceWritePolicy.rejectionOf(combined))
                    .isEqualTo(ReferenceWritePolicy.rejectionOf(((ReferenceStepOutcome.Failed) firstFailure)
                            .failure()
                            .reason()));
        } else if (firstMissing != null) {
            assertThat(combined).isEqualTo(firstMissing);
        } else if (anyInvalid) {
            assertThat(combined).isInstanceOf(ReferenceStepOutcome.Invalid.class);
        } else if (anyRejected) {
            assertThat(combined).isInstanceOf(ReferenceStepOutcome.Rejected.class);
            assertThat(ReferenceWritePolicy.fieldErrorsOf(combined)).hasSize((int) partials.stream()
                    .filter(p -> p instanceof ReferenceStepOutcome.Rejected)
                    .count());
        } else {
            assertThat(combined).isEqualTo(new ReferenceStepOutcome.Proceed());
        }
    }

    @Property
    @Label("NOT_FOUND_OR_DENIED is always REFERENCE_NOT_FOUND for the column")
    void notFoundIsAlwaysReferenceNotFound(@ForAll("columns") String column) {
        ReferenceStepOutcome outcome =
                ReferenceWritePolicy.ofCheck(column, new ReferenceCheckResult.NotFoundOrDenied());

        assertThat(ReferenceWritePolicy.rejectionOf(outcome)).isEqualTo(WriteRejection.REFERENCE_NOT_FOUND);
        assertThat(ReferenceWritePolicy.fieldErrorsOf(outcome))
                .containsExactly(new BusinessDataFieldError(column, BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND));
    }

    @Provide
    Arbitrary<String> columns() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
    }
}
