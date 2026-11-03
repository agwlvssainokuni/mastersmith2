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
package cherry.mastersmith.businessdata.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** セルの値の文字列表現が値を出さないことの単体テスト（TraceAspect が TRACE に文字列で出すため）。 */
class CellValueTest {

    @Test
    @DisplayName("Raw hides the text it holds")
    void rawHidesText() {
        assertThat(new CellValue.Raw("0000-00-00").toString())
                .doesNotContain("0000")
                .contains("***");
    }

    @Test
    @DisplayName("Text hides the text it holds")
    void textHidesText() {
        assertThat(new CellValue.Text("2026-01-01", false).toString()).doesNotContain("2026");
    }

    @Test
    @DisplayName("Unreadable holds only the exception class name")
    void unreadableHoldsClassNameOnly() {
        assertThat(new CellValue.Unreadable("java.lang.IllegalStateException").toString())
                .contains("IllegalStateException");
    }
}
