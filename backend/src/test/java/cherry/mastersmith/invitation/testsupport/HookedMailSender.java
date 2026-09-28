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
package cherry.mastersmith.invitation.testsupport;

import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.mail.service.SmtpMailSender;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 本物の送信の部品（U1 の {@code SmtpMailSender}）に渡す前に、1回だけ操作を差し込めるテストの包み（送信の最中の同時の送り直しを
 * 作るため。{@code reliability-design.md} 6節）。差し込まないときは本物にそのまま渡す（送信をモックにしない）。
 */
public class HookedMailSender implements MailSender {

    private final SmtpMailSender delegate;

    private final AtomicReference<Supplier<MailSendResult>> next = new AtomicReference<>();

    /**
     * 作る。
     *
     * @param delegate 本物の送信の部品
     */
    public HookedMailSender(SmtpMailSender delegate) {
        this.delegate = delegate;
    }

    /**
     * 次の1回の送信の代わりに行う操作を決める（操作の結果を送信の結果として返す）。
     *
     * @param action 操作
     */
    public void onNextSend(Supplier<MailSendResult> action) {
        next.set(action);
    }

    @Override
    public boolean isConfigured() {
        return delegate.isConfigured();
    }

    @Override
    public MailSendResult send(MailRequest request) {
        Supplier<MailSendResult> action = next.getAndSet(null);
        return action == null ? delegate.send(request) : action.get();
    }

    /** テストで差し替える設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * 本物の送信の部品を包む。
         *
         * @param delegate 本物の送信の部品
         * @return 包んだ送信の部品
         */
        @Bean
        @Primary
        public HookedMailSender hookedMailSender(SmtpMailSender delegate) {
            return new HookedMailSender(delegate);
        }
    }
}
