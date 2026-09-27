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

import cherry.mastersmith.mail.domain.MailFailureKind;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

/**
 * 送信の失敗の分類（BR5.3、logical-components.md の 5.3 の表）。例外を投げない純粋な関数。
 *
 * <p>送信の部品の例外の原因の連なり（{@code getCause}、Jakarta Mail の {@code getNextException}、Spring の
 * {@code MailSendException.getMessageExceptions}、抑制された例外）の型だけで分類し、文言は見ない。Angus Mail は実行時だけの依存の
 * ため、Jakarta Mail の API と Spring の型だけを使う。
 */
public final class SendFailureClassifier {

    /** 原因の連なりをたどる数の上限（連なりが輪になっても止まるようにする）。 */
    static final int MAX_CAUSES = 32;

    private SendFailureClassifier() {}

    /**
     * 失敗を分類する。
     *
     * @param failure 送信の失敗
     * @return 失敗の種類。表の4行目（想定外）なら空
     */
    public static Optional<MailFailureKind> classify(Throwable failure) {
        List<Throwable> chain = chain(failure);
        if (chain.stream().anyMatch(SocketTimeoutException.class::isInstance)) {
            return Optional.of(MailFailureKind.TIMEOUT);
        }
        if (chain.stream()
                .anyMatch(t -> t instanceof MailAuthenticationException
                        || t instanceof AuthenticationFailedException
                        || t instanceof SendFailedException)) {
            return Optional.of(MailFailureKind.REJECTED);
        }
        if (chain.stream().anyMatch(MailSendException.class::isInstance)) {
            return Optional.of(MailFailureKind.CONNECTION_FAILED);
        }
        return Optional.empty();
    }

    /** 原因の連なりを幅優先でたどる（同じ例外は1回だけ、数に上限）。 */
    static List<Throwable> chain(Throwable failure) {
        List<Throwable> result = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> queue = new ArrayDeque<>();
        if (failure != null) {
            queue.add(failure);
        }
        while (!queue.isEmpty() && result.size() < MAX_CAUSES) {
            Throwable current = queue.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            result.add(current);
            if (current.getCause() != null) {
                queue.add(current.getCause());
            }
            if (current instanceof MessagingException messaging && messaging.getNextException() != null) {
                queue.add(messaging.getNextException());
            }
            if (current instanceof MailSendException send) {
                Collections.addAll(queue, send.getMessageExceptions());
            }
            Collections.addAll(queue, current.getSuppressed());
        }
        return result;
    }
}
