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
package cherry.mastersmith.mail.transport;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.mail.domain.MailFailureKind;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

/** 送信の失敗の分類の単体テスト（logical-components.md の 5.3 の表のすべての行、BR5.3、NFR9.2）。 */
class SendFailureClassifierTest {

    private static final String RECIPIENT = "taro@example.com";

    private static MailSendException connectionFailed(Exception cause) {
        return new MailSendException("Mail server connection failed", cause);
    }

    private static MailSendException perMessage(Exception failure) {
        return new MailSendException(Map.of(new Object(), failure));
    }

    @Test
    @DisplayName("row 1: a socket timeout wrapped by the connection failure is TIMEOUT")
    void timeout() {
        MessagingException connect =
                new MessagingException("Could not connect to SMTP host", new SocketTimeoutException("Read timed out"));

        assertThat(SendFailureClassifier.classify(connectionFailed(connect))).contains(MailFailureKind.TIMEOUT);
    }

    @Test
    @DisplayName("row 1: a timeout found only as the next exception of MessagingException is TIMEOUT")
    void timeoutAsNextException() {
        MessagingException outer = new MessagingException("outer");
        outer.setNextException(new SocketTimeoutException("connect timed out"));

        assertThat(SendFailureClassifier.classify(perMessage(outer))).contains(MailFailureKind.TIMEOUT);
    }

    @Test
    @DisplayName("row 2: authentication failures are REJECTED")
    void authenticationRejected() {
        assertThat(SendFailureClassifier.classify(
                        new MailAuthenticationException(new AuthenticationFailedException("535"))))
                .contains(MailFailureKind.REJECTED);
        assertThat(SendFailureClassifier.classify(connectionFailed(new AuthenticationFailedException("535"))))
                .contains(MailFailureKind.REJECTED);
    }

    @Test
    @DisplayName("row 2: a recipient refusal inside the message exceptions is REJECTED")
    void recipientRejected() {
        SendFailedException refused = new SendFailedException("550 5.1.1 user unknown " + RECIPIENT);

        assertThat(SendFailureClassifier.classify(perMessage(refused))).contains(MailFailureKind.REJECTED);
    }

    @Test
    @DisplayName("row 3: other send failures such as a refused connection or a TLS failure are CONNECTION_FAILED")
    void connectionFailed() {
        assertThat(SendFailureClassifier.classify(
                        connectionFailed(new MessagingException("x", new ConnectException("refused")))))
                .contains(MailFailureKind.CONNECTION_FAILED);
        assertThat(SendFailureClassifier.classify(
                        connectionFailed(new MessagingException("x", new SSLHandshakeException("bad")))))
                .contains(MailFailureKind.CONNECTION_FAILED);
        assertThat(SendFailureClassifier.classify(connectionFailed(
                        new MessagingException("STARTTLS is required but host does not support STARTTLS"))))
                .contains(MailFailureKind.CONNECTION_FAILED);
    }

    @Test
    @DisplayName("row 4: preparation and parse failures and other runtime exceptions are unexpected")
    void unexpected() {
        assertThat(SendFailureClassifier.classify(new MailPreparationException("prepare")))
                .isEmpty();
        assertThat(SendFailureClassifier.classify(new MailParseException("parse")))
                .isEmpty();
        assertThat(SendFailureClassifier.classify(new IllegalStateException("bug")))
                .isEmpty();
        assertThat(SendFailureClassifier.classify(null)).isEmpty();
    }

    @Test
    @DisplayName("the message text never changes the classification")
    void messageTextIsIgnored() {
        assertThat(SendFailureClassifier.classify(new MailPreparationException("timeout rejected 550 " + RECIPIENT)))
                .isEmpty();
        assertThat(SendFailureClassifier.classify(
                        connectionFailed(new MessagingException("Read timed out " + RECIPIENT))))
                .contains(MailFailureKind.CONNECTION_FAILED);
    }

    @Test
    @DisplayName("a cyclic chain stops")
    void cyclicChain() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.addSuppressed(second);

        assertThat(SendFailureClassifier.classify(second)).isEmpty();
        assertThat(SendFailureClassifier.chain(second)).hasSize(2);
    }
}
