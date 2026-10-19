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
package cherry.mastersmith.businessdata.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 読むたびに決めた ms だけ進む時計（U2 の pattern の締め切りの単体テスト。実時刻と {@code sleep} に頼らない）。
 */
public final class SteppingClock extends Clock {

    private final AtomicLong millis;

    private final long step;

    /**
     * 作る。
     *
     * @param startMillis 始めの値（ms）
     * @param step 読むたびに進める ms
     */
    public SteppingClock(long startMillis, long step) {
        this.millis = new AtomicLong(startMillis);
        this.step = step;
    }

    /**
     * 読んだ回数に関わらず、時計を進める。
     *
     * @param amount 進める ms
     */
    public void advance(long amount) {
        millis.addAndGet(amount);
    }

    /**
     * 今の値（ms）を、進めずに返す。
     *
     * @return 今の値
     */
    public long peek() {
        return millis.get();
    }

    @Override
    public long millis() {
        return millis.getAndAdd(step);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
