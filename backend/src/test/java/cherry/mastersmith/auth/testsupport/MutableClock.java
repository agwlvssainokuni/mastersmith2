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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** テストで時刻を進める時計（UTC）。{@code sleep} や実時間に頼らずに有効期限やロックの解除を確かめる（NFR9.4）。 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;

    /**
     * 始めの時刻を指定して作る。
     *
     * @param start 始めの時刻
     */
    public MutableClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    /**
     * 時刻を指定した時点にする。
     *
     * @param instant 時点
     */
    public void set(Instant instant) {
        now.set(instant);
    }

    /**
     * 時刻を進める。
     *
     * @param duration 進める時間
     */
    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("テスト用の時計はタイムゾーンを変えない");
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
