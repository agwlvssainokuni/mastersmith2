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
package cherry.mastersmith.audit.testsupport;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 書き込みの時間の測り方を差し替え、1件の書き込みが遅れの目安を超えた状態を、実時間に頼らずに作る（計画の C5）。
 *
 * <p>呼ばれるたびに一定の幅（既定は 250 ミリ秒）だけ進む時計を返すため、開始と終了の2回の呼び出しで必ず目安を超える。
 */
@TestConfiguration(proxyBeanMethods = false)
public class SlowAuditWriteConfig {

    /** 1回の呼び出しで進む時間（ナノ秒）。遅れの目安（200 ミリ秒）を超える値にする。 */
    public static final long STEP_NANOS = 250_000_000L;

    /**
     * 呼ばれるたびに一定の幅で進む、書き込みの時間の測り方を置く。
     *
     * @return 経過時間の測り方
     */
    @Bean
    @Primary
    public LongSupplier slowAuditWriteNanoTime() {
        AtomicLong nanos = new AtomicLong();
        return () -> nanos.getAndAdd(STEP_NANOS);
    }
}
