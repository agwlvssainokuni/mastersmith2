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
package cherry.mastersmith.auth.testsupport;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.crypto.password.PasswordEncoder;

/** パスワードの照合（{@code matches}）の回数を数える包み（BR2.5 の確かめ）。 */
public class CountingPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;

    private final AtomicInteger matches = new AtomicInteger();

    /**
     * 包む対象を指定して作る。
     *
     * @param delegate 実際の照合の仕組み
     */
    public CountingPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        matches.incrementAndGet();
        return delegate.matches(rawPassword, encodedPassword);
    }

    /**
     * 照合の回数を返し、0 に戻す。
     *
     * @return 前に戻してからの照合の回数
     */
    public int takeMatchCount() {
        return matches.getAndSet(0);
    }
}
