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

import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * 送信の依頼の確かめの性質ベースのテスト（jqwik、team.md の Testing Posture）。
 *
 * <p>失敗したときの乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する）。
 */
class MailRequestValidationPropertyTest {

    private static final Set<String> NAMES = Set.of("value");

    @Provide
    Arbitrary<String> anyText() {
        return Arbitraries.strings().all().ofMaxLength(40);
    }

    @Provide
    Arbitrary<String> blankText() {
        return Arbitraries.strings()
                .withChars(' ', '\t', '　', '\u000B', '\f', ' ', '\u001C')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    private static String withBreak(String text, int position, boolean cr) {
        int at = Math.min(position, text.length());
        return text.substring(0, at) + (cr ? "\r" : "\n") + text.substring(at);
    }

    @Property(tries = 300)
    @Label("a CR or LF anywhere in the recipient or a value always gives INVALID_INPUT")
    void lineBreakIsAlwaysRejected(
            @ForAll("anyText") String text,
            @ForAll @IntRange(max = 40) int position,
            @ForAll boolean cr,
            @ForAll boolean inRecipient) {
        String broken = withBreak(text, position, cr);
        MailRequest request = inRecipient
                ? new MailRequest("sample", "ja", broken, Map.of("value", "ok"))
                : new MailRequest("sample", "ja", "taro@example.com", Map.of("value", broken));

        assertThat(MailRequestValidation.validate(request, true, NAMES)).contains(MailFailureKind.INVALID_INPUT);
    }

    @Property(tries = 300)
    @Label("a value made only of whitespace always gives INVALID_INPUT")
    void blankValueIsAlwaysRejected(@ForAll("blankText") String blank) {
        MailRequest request = new MailRequest("sample", "en", "taro@example.com", Map.of("value", blank));

        assertThat(MailRequestValidation.validate(request, true, NAMES)).contains(MailFailureKind.INVALID_INPUT);
    }

    @Property(tries = 500)
    @Label("a recipient accepted by the address rule never contains CR or LF and is at most 254 characters")
    void acceptedRecipientIsSafe(@ForAll("anyText") String local, @ForAll("anyText") String domain) {
        String candidate = local + "@" + domain + ".example";

        if (MailAddressRule.isValid(candidate)) {
            assertThat(candidate).doesNotContain("\r").doesNotContain("\n");
            assertThat(candidate.length()).isLessThanOrEqualTo(MailAddressRule.MAX_LENGTH);
        }
    }
}
