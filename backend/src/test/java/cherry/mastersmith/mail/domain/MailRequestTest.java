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
package cherry.mastersmith.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 送信の依頼の値の型の単体テスト（NFR1.2）。 */
class MailRequestTest {

    @Test
    @DisplayName("toString shows only the template id and the language, never the recipient or the values")
    void toStringHidesRecipientAndValues() {
        MailRequest request = new MailRequest(
                "sample", "ja", "taro@example.com", Map.of("name", "山田太郎", "link", "https://example.com/t/abc"));

        assertThat(request.toString())
                .isEqualTo("MailRequest[templateId=sample, language=ja]")
                .doesNotContain("taro@example.com")
                .doesNotContain("山田太郎")
                .doesNotContain("https://example.com/t/abc");
    }

    @Test
    @DisplayName("the variables are copied and cannot be changed from outside")
    void variablesAreAnUnmodifiableCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("name", "花子");
        MailRequest request = new MailRequest("sample", "ja", "hanako@example.com", source);

        source.put("name", "変えた値");
        source.put("extra", "足した値");

        assertThat(request.variables()).containsExactlyEntriesOf(Map.of("name", "花子"));
        assertThatThrownBy(() -> request.variables().put("other", "x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null values are kept so that the validation can reject them, and a null map becomes empty")
    void nullValuesAreKept() {
        Map<String, String> source = new HashMap<>();
        source.put("name", null);

        assertThat(new MailRequest("sample", "ja", "a@example.com", source).variables())
                .containsEntry("name", null);
        assertThat(new MailRequest("sample", "ja", "a@example.com", null).variables())
                .isEmpty();
    }
}
