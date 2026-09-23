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
package cherry.mastersmith.access.testsupport;

import cherry.mastersmith.auth.testsupport.MutableClock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * U3 のアクセス制御の結合テストの設定。時刻を進める時計（U2 の {@link MutableClock} を {@code @Primary} で差し替える）と、
 * アクセス拒否の出来事の受け取りを Bean として置く。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AdminAccessTestConfig {

    /** テストの基準の時刻。 */
    public static final Instant START = Instant.parse("2026-09-22T00:00:00Z");

    /**
     * 時刻を進める時計。
     *
     * @return 時計
     */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(START);
    }

    /**
     * アクセス拒否の出来事の受け取り。
     *
     * @return 出来事の受け取り
     */
    @Bean
    public CapturedAccessDeniedEvents capturedAccessDeniedEvents() {
        return new CapturedAccessDeniedEvents();
    }
}
