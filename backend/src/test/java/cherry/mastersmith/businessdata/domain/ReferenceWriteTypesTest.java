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

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 保存前の参照の段の値の型の単体テスト（Intent 261029-reference-lookup-cleanup の U2、BR7.1、entities.md の制約）。 */
class ReferenceWriteTypesTest {

    private static final String SECRET = "社外秘の参照値<script>";

    @Test
    @DisplayName("the value holding types print only column names, origins and counts")
    void textFormsHideValues() {
        ReferenceTarget target = ReferenceTarget.from("secret_schema", BusinessDataDslFixture.reference())
                .orElseThrow();
        ReferenceCheckItem item = new ReferenceCheckItem(
                "dept_code", target, new ReferenceValue(SECRET), ReferenceCheckItem.Origin.UPDATE_CHANGED);
        Map<String, BoundValue> previous = new LinkedHashMap<>();
        previous.put("dept_code", new BoundValue(ValueType.STRING, SECRET));
        PreviousReferenceValues values = new PreviousReferenceValues(previous);
        ReferenceStepOutcome invalid = new ReferenceStepOutcome.Invalid(
                List.of(new BusinessDataFieldError("dept_code", BusinessDataFieldErrorReason.REQUIRED)));

        assertThat(item.toString())
                .isEqualTo("ReferenceCheckItem[column=dept_code, origin=UPDATE_CHANGED]")
                .doesNotContain(SECRET)
                .doesNotContain("secret_schema");
        assertThat(values.toString()).isEqualTo("PreviousReferenceValues[columns=[dept_code]]");
        assertThat(invalid.toString()).isEqualTo("Invalid[fieldErrors=1]");
        assertThat(values.previous("dept_code")).isPresent();
        assertThat(values.previous("other")).isEmpty();
    }

    @Test
    @DisplayName("the outcome types refuse empty error lists and rows that are not missing rows")
    void outcomeInvariants() {
        assertThatThrownBy(() -> new ReferenceStepOutcome.Invalid(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceStepOutcome.Rejected(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceStepOutcome.RowMissing(WriteRejection.REFERENCE_NOT_FOUND))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequiredReferenceState("c", null, RequiredReferenceState.BodyState.VALUE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Valid keeps its old text form without held errors and adds only their number")
    void validTextForm() {
        WriteValidation.Valid plain = new WriteValidation.Valid(List.of(), null);
        WriteValidation.Valid held = new WriteValidation.Valid(
                List.of(), null, List.of(new BusinessDataFieldError("c", BusinessDataFieldErrorReason.REQUIRED)));

        assertThat(plain.toString()).isEqualTo("Valid[values=0, key=null]");
        assertThat(plain.held()).isEmpty();
        assertThat(held.toString()).isEqualTo("Valid[values=0, key=null, held=1]");
    }
}
