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
package cherry.mastersmith.dsl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 結果の型・誤り・文言の鍵の単体テスト（契約 C4・C8、BR4.3・BR5.4、NFR5.2）。 */
class DslResultTypesTest {

    private static DslModel model() {
        return new DslModel(DslModelTest.HASH, 1, List.of(), Map.of("dept", DslModelTest.table("dept", "code")));
    }

    private static String describe(DslReadResult result) {
        return switch (result) {
            case DslReadResult.Valid valid -> "valid:" + valid.dslHash();
            case DslReadResult.Invalid invalid -> "invalid:" + invalid.errors().size();
        };
    }

    private static String describe(ActiveDsl active) {
        return switch (active) {
            case ActiveDsl.Present present -> "present:" + present.dslHash();
            case ActiveDsl.Absent absent -> "absent";
        };
    }

    @Test
    @DisplayName("read results are either valid with the model hash or invalid with at least one error")
    void readResultVariants() {
        DslError error = new DslError(DslErrorKind.SIZE_LIMIT, null, null, null, DslMessageKeys.SIZE_LIMIT, List.of());

        assertThat(describe(DslReadResult.valid(model()))).isEqualTo("valid:" + DslModelTest.HASH);
        assertThat(describe(DslReadResult.invalid(List.of(error)))).isEqualTo("invalid:1");
        assertThatThrownBy(() -> DslReadResult.invalid(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslReadResult.Valid(model(), "f".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        List<DslError> errors = new ArrayList<>(List.of(error));
        DslReadResult.Invalid invalid = new DslReadResult.Invalid(errors);
        errors.clear();
        assertThat(invalid.errors()).hasSize(1);
    }

    @Test
    @DisplayName("the active DSL is present with the model hash, or absent when there is no model")
    void activeDslVariants() {
        assertThat(describe(ActiveDsl.of(model()))).isEqualTo("present:" + DslModelTest.HASH);
        assertThat(describe(ActiveDsl.of(null))).isEqualTo("absent");
        assertThatThrownBy(() -> new ActiveDsl.Present(model(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActiveDsl.Present(null, DslModelTest.HASH))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("an error has both line and column or neither, starting at 1")
    void errorPosition() {
        DslError error = new DslError(
                DslErrorKind.SYNTAX, 3, 5, "tables.dept", DslMessageKeys.SYNTAX_REQUIRED, List.of("columns"));
        assertThat(error.line()).isEqualTo(3);
        assertThat(error.messageArgs()).containsExactly("columns");

        assertThatThrownBy(() -> new DslError(DslErrorKind.SYNTAX, 1, null, null, "k", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslError(DslErrorKind.SYNTAX, 0, 1, null, "k", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslError(DslErrorKind.SYNTAX, null, null, "", "k", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslError(null, null, null, null, "k", List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("user values are cut to the first 100 characters, counting surrogate pairs as one")
    void excerptCutsTo100Characters() {
        assertThat(DslError.excerpt(null)).isEmpty();
        assertThat(DslError.excerpt("あ".repeat(100))).hasSize(100);
        assertThat(DslError.excerpt("x".repeat(101))).isEqualTo("x".repeat(100));
        String emoji = "😀".repeat(101);
        assertThat(DslError.excerpt(emoji)
                        .codePointCount(0, DslError.excerpt(emoji).length()))
                .isEqualTo(100);
    }

    @Test
    @DisplayName("message keys are unique and all start with dsl.")
    void messageKeysAreUnique() {
        List<String> keys = DslMessageKeys.all();

        assertThat(keys).isNotEmpty().allMatch(key -> key.startsWith("dsl."));
        assertThat(new HashSet<>(keys)).hasSameSizeAs(keys);
        assertThatThrownBy(() -> keys.add("dsl.x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the format constants hold the decided limits")
    void formatLimits() {
        assertThat(DslFormat.CURRENT_VERSION).isEqualTo(1);
        assertThat(DslFormat.MAX_BYTES).isEqualTo(10_485_760);
        assertThat(DslFormat.MAX_DEPTH).isEqualTo(50);
        assertThat(DslFormat.MAX_COLLECTION_ALIASES).isEqualTo(100);
        assertThat(DslFormat.MAX_EXPANDED_NODES).isEqualTo(1_000_000);
        assertThat(DslFormat.MAX_PATTERN_LENGTH).isEqualTo(1_000);
        assertThat(DslFormat.PATTERN_CHECK_TIMEOUT.toMillis()).isEqualTo(100);
        assertThat(DslFormat.MAX_ARGUMENT_LENGTH).isEqualTo(100);
    }
}
