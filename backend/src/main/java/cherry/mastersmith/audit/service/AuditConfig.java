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
package cherry.mastersmith.audit.service;

import java.util.function.LongSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * U4 の設定。書き込みの時間の測り方だけを置く（設定の型・環境変数は足さない）。
 *
 * <p>測り方は U1 の {@code DummyPasswordHash} と同じく差し替えられる {@link LongSupplier}（既定は {@code System::nanoTime}）
 * にし、テストでは {@code @Primary} の Bean で差し替えて実時間に頼らずに遅れの WARN を確かめる（計画の C5）。
 */
@Configuration(proxyBeanMethods = false)
public class AuditConfig {

    /**
     * 書き込みの時間の測り方（ナノ秒）。
     *
     * @return 経過時間の測り方
     */
    @Bean
    public LongSupplier auditWriteNanoTime() {
        return System::nanoTime;
    }
}
