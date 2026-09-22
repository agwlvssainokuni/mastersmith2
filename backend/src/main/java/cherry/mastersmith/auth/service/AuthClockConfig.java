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
package cherry.mastersmith.auth.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 現在時刻を得る時計（UTC）。有効期限・ロックの解除・出来事の日時は、この時計から得た時点（{@code Instant}）で保存・比較する
 * （NFR9.4）。
 *
 * <p>アプリで1つだけ置き、U3・U4 もこの Bean を使う（別に定義しない）。テストでは {@code @Primary} の時計で差し替える。
 */
@Configuration(proxyBeanMethods = false)
public class AuthClockConfig {

    /**
     * UTC の時計を作る。
     *
     * @return 時計
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
