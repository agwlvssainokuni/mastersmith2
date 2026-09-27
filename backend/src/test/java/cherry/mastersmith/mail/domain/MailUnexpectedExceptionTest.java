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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailPreparationException;

/** 想定外の失敗の例外の単体テスト（security-design.md の 4.3、NFR 設計の承認の場の U1 R-02）。 */
class MailUnexpectedExceptionTest {

    private static final String RECIPIENT = "secret-recipient@example.com";

    @Test
    @DisplayName(
            "the message is fixed and holds neither the recipient nor the component's message, and no cause is attached")
    void fixedMessageWithoutCause() {
        MailUnexpectedException exception =
                MailUnexpectedException.of(new MailPreparationException("could not prepare " + RECIPIENT));

        assertThat(exception.getMessage())
                .startsWith(MailUnexpectedException.MESSAGE)
                .doesNotContain(RECIPIENT)
                .doesNotContain("could not prepare");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStackTrace()).as("包んだ地点のスタックトレースは残る").isNotEmpty();
    }

    @Test
    @DisplayName(
            "the type names of the whole cause chain are listed, including the next exception of MessagingException")
    void listsTypeNamesOfTheChain() {
        MessagingException messaging = new MessagingException("outer " + RECIPIENT);
        messaging.setNextException(new AddressException("bad " + RECIPIENT));
        RuntimeException outer = new IllegalStateException("wrap " + RECIPIENT, messaging);
        outer.addSuppressed(new IOException("suppressed"));

        MailUnexpectedException exception = MailUnexpectedException.of(outer);

        assertThat(exception.causeTypeNames())
                .containsExactly(
                        IllegalStateException.class.getName(),
                        MessagingException.class.getName(),
                        IOException.class.getName(),
                        AddressException.class.getName());
        assertThat(exception.getMessage()).doesNotContain(RECIPIENT);
    }

    @Test
    @DisplayName("a cyclic cause chain stops and every exception is listed once")
    void cyclicChainStops() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.addSuppressed(second);

        assertThat(MailUnexpectedException.of(second).causeTypeNames())
                .containsExactly(RuntimeException.class.getName(), RuntimeException.class.getName());
    }

    @Test
    @DisplayName("a very long chain is cut at the upper limit")
    void longChainIsCut() {
        Throwable chain = new RuntimeException("root");
        for (int i = 0; i < 40; i++) {
            chain = new IllegalArgumentException("level " + i, chain);
        }

        List<String> names = MailUnexpectedException.of(chain).causeTypeNames();

        assertThat(names).hasSize(MailUnexpectedException.MAX_CAUSES);
    }

    @Test
    @DisplayName("a null cause gives an empty list")
    void nullCause() {
        assertThat(MailUnexpectedException.of(null).causeTypeNames()).isEmpty();
    }
}
