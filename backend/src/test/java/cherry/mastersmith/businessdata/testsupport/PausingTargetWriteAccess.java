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

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetWriteOutcome;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import cherry.mastersmith.targetdb.domain.WriteStep;
import cherry.mastersmith.targetdb.service.TargetWriteAccess;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 書き込みの口を包み、{@code withWrite} の入口で一度きりの合図で待ち合わせるテストだけの部品（Intent 261029-reference-lookup-cleanup の
 * U2、reliability-design.md 4節、T-G1・T-G2）。保存前の参照の段の後・書き込みの接続を借りる前に止める。
 *
 * <p>合図は {@link #arm()} で1組だけ立て、入口で取り出した1回の要求だけを止める（ほかの要求は止めない）。待ちは上限つき（
 * {@link #LIMIT_SECONDS} 秒）。上限で切れたら元の {@code withWrite} を呼ばずに例外で要求を終え、{@link #timedOut()} の印を残す。
 * 時計に頼らず {@code sleep} を使わない。
 */
public final class PausingTargetWriteAccess implements TargetWriteAccess {

    /** 待ちの上限（秒）。 */
    public static final long LIMIT_SECONDS = 20;

    private final TargetWriteAccess delegate;

    private final AtomicReference<Gate> armed = new AtomicReference<>();

    private final AtomicBoolean timedOut = new AtomicBoolean();

    /**
     * 包む。
     *
     * @param delegate 元の書き込みの口
     */
    public PausingTargetWriteAccess(TargetWriteAccess delegate) {
        this.delegate = delegate;
    }

    /**
     * 次の1回の要求を入口で止める合図を立てる。
     *
     * @return 合図（着いたかを待ち、進めさせる）
     */
    public Gate arm() {
        Gate gate = new Gate(new CountDownLatch(1), new CountDownLatch(1));
        armed.set(gate);
        return gate;
    }

    /** 取り出されなかった合図を外し、上限切れの印を消す（テストごとの後始末）。 */
    public void reset() {
        Gate gate = armed.getAndSet(null);
        if (gate != null) {
            gate.release();
        }
        timedOut.set(false);
    }

    /**
     * 止めた要求の「進め」の待ちが上限で切れたかを返す。
     *
     * @return 切れたなら true
     */
    public boolean timedOut() {
        return timedOut.get();
    }

    @Override
    public <T> TargetWriteOutcome<T> withWrite(Function<TargetWriteSession, WriteStep<T>> work) {
        Gate gate = armed.getAndSet(null);
        if (gate != null) {
            gate.arrived().countDown();
            try {
                if (!gate.proceed().await(LIMIT_SECONDS, TimeUnit.SECONDS)) {
                    timedOut.set(true);
                    throw new IllegalStateException("待ち合わせの上限を超えました");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                timedOut.set(true);
                throw new IllegalStateException("待ち合わせが中断されました");
            }
        }
        return delegate.withWrite(work);
    }

    @Override
    public Optional<DatabaseProduct> product() {
        return delegate.product();
    }

    /**
     * 一度きりの合図の組。
     *
     * @param arrived 入口に着いた
     * @param proceed 進めさせる
     */
    public record Gate(CountDownLatch arrived, CountDownLatch proceed) {

        /**
         * 入口に着くのを上限つきで待つ。
         *
         * @return 着いたなら true
         * @throws InterruptedException 中断されたとき
         */
        public boolean awaitArrival() throws InterruptedException {
            return arrived.await(LIMIT_SECONDS, TimeUnit.SECONDS);
        }

        /** 止めた要求を進めさせる（何度呼んでもよい）。 */
        public void release() {
            proceed.countDown();
        }
    }
}
