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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DSL の誤り1つ（{@link DslError}）の文字列化の単体テスト（Intent 261015-deps-ui-carryover の FR3.1）。利用者が書いた値（{@code path}
 * の中の名前と埋める値）が出ず、種類・位置・文言の鍵・埋める値の件数が出ることを確かめる。
 */
class DslErrorTest {

    @Test
    @DisplayName(
            "toString prints the kind, position, message key and the number of arguments but no user-written value")
    void toStringHidesUserValues() {
        DslError error = new DslError(
                DslErrorKind.DUPLICATE_KEY,
                24,
                1,
                "schemas.MARKER_PATH",
                DslMessageKeys.DUPLICATE_KEY,
                List.of("MARKER_ARG_1", "MARKER_ARG_2"));

        assertThat(error.toString())
                .isEqualTo("DslError[kind=DUPLICATE_KEY, line=24, column=1, messageKey=dsl.key.duplicate,"
                        + " messageArgs=2 items]")
                .doesNotContain("MARKER_PATH", "MARKER_ARG_1", "MARKER_ARG_2");
        assertThat(error.path()).isEqualTo("schemas.MARKER_PATH");
        assertThat(error.messageArgs()).containsExactly("MARKER_ARG_1", "MARKER_ARG_2");
    }

    @Test
    @DisplayName("toString of an error without a position or arguments prints null and zero")
    void toStringWithoutPosition() {
        DslError error = new DslError(DslErrorKind.SYNTAX, null, null, null, DslMessageKeys.DUPLICATE_KEY, List.of());

        assertThat(error.toString())
                .isEqualTo("DslError[kind=SYNTAX, line=null, column=null,"
                        + " messageKey=dsl.key.duplicate, messageArgs=0 items]");
    }

    @Test
    @DisplayName("the invalid read result prints the errors without their user-written values")
    void invalidResultHidesUserValues() {
        DslError error = new DslError(
                DslErrorKind.SEMANTIC,
                4,
                5,
                "menus.0.table",
                DslMessageKeys.SEMANTIC_UNKNOWN_TABLE,
                List.of("public.MARKER_TABLE"));

        assertThat(DslReadResult.invalid(List.of(error)).toString())
                .contains("DslError[kind=SEMANTIC, line=4, column=5")
                .doesNotContain("MARKER_TABLE", "menus.0.table");
    }
}
