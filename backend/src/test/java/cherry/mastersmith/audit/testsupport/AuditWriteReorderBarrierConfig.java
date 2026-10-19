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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 2つの要求の監査の書き込みの順を、待ち合わせで決めるテストの補助（Intent 261016-business-data-views の U5、
 * {@code logical-components.md} 4節）。
 *
 * <p>監査の受け取り（{@code AuditEventListener}）は、確定の後に要求のスレッドで、書き込みの時間を測る呼び出し（{@link LongSupplier}）を
 * 書き込みの直前と終わりの2回呼ぶ。この呼び出しを差し替え、止める相手のスレッドの書き込みを、放す相手のスレッドの書き込みが終わるまで
 * 止める。待ち合わせはスレッドだけを受け取り、ロールなどの業務の機能を知らない（依存の向きは役割の側からこの補助へだけ）。
 *
 * <p>同じ型を {@code @Primary} で差し替える {@link AuditWriteBarrierConfig}・{@link SlowAuditWriteConfig} と同じテストのクラスに入れない。
 * 入れると Spring が {@code @Primary} の重なりで起動を止めるため、入れ違いは起動の失敗で分かる。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuditWriteReorderBarrierConfig {

    /**
     * 待ち合わせを置く。
     *
     * @return 待ち合わせ
     */
    @Bean
    public AuditWriteReorderBarrier auditWriteReorderBarrier() {
        return new AuditWriteReorderBarrier();
    }

    /**
     * 書き込みの時間の測り方を、待ち合わせを通してから時刻を返すものに差し替える。
     *
     * @param barrier 待ち合わせ
     * @return 経過時間の測り方
     */
    @Bean
    @Primary
    public LongSupplier reorderAuditWriteNanoTime(AuditWriteReorderBarrier barrier) {
        return () -> {
            barrier.pass();
            return System.nanoTime();
        };
    }

    /**
     * 止める相手のスレッドの監査の書き込みの直前で止め、放す相手のスレッドの監査の書き込みが終わったら放す待ち合わせ。
     *
     * <ul>
     *   <li>準備していない間は、すべての呼び出しを止めずに通す
     *   <li>スレッドごとの呼び出しの回数は、{@link #armFor(Thread, Thread, Duration)} の後に数え始める
     *   <li>止めるのは止める相手の1回目（書き込みの直前）だけ。2回目（書き込みの終わり）は止めない
     *   <li>放すのは放す相手の2回目（書き込みの終わり）だけ。ほかのスレッドの呼び出しでは放さない
     *   <li>放す条件が先に満たされていたら、止めずに通す（{@link #heldArrived()} は真、{@link #releasedByTimeout()} は偽）
     *   <li>上限で放したら {@link #releasedByTimeout()} を真にする
     * </ul>
     *
     * <p>状態は1つのロックで守る。止めている間に割り込まれたら、割り込みの印を戻して先へ進ませる。
     */
    public static final class AuditWriteReorderBarrier {

        private final Object lock = new Object();

        private boolean armed;

        /** 準備の回の番号（前の回で止めたスレッドを、新しい回の準備で必ず放すため）。 */
        private long generation;

        private Thread held;

        private Thread releaser;

        private Duration waitLimit = Duration.ZERO;

        private int heldCalls;

        private int releaserCalls;

        private boolean heldArrived;

        private boolean released;

        private boolean releasedByTimeout;

        /**
         * 止める相手と放す相手を決めて準備する。前の回の状態（止めた・放した・上限で放した・止める相手が来た）は初めに戻す。
         *
         * @param held 止める相手のスレッド
         * @param releaser 放す相手のスレッド
         * @param waitLimit 止める時間の上限（正の値）
         * @throws IllegalArgumentException 2つのスレッドが同じとき、上限が正でないとき
         */
        public void armFor(Thread held, Thread releaser, Duration waitLimit) {
            Objects.requireNonNull(held, "止める相手のスレッドがありません");
            Objects.requireNonNull(releaser, "放す相手のスレッドがありません");
            Objects.requireNonNull(waitLimit, "上限がありません");
            if (held == releaser) {
                throw new IllegalArgumentException("止める相手と放す相手が同じスレッドです: " + held.getName());
            }
            if (waitLimit.isNegative() || waitLimit.isZero()) {
                throw new IllegalArgumentException("上限は正の値にしてください: " + waitLimit);
            }
            synchronized (lock) {
                generation++;
                this.held = held;
                this.releaser = releaser;
                this.waitLimit = waitLimit;
                heldCalls = 0;
                releaserCalls = 0;
                heldArrived = false;
                released = false;
                releasedByTimeout = false;
                armed = true;
                // 前の回で止めているスレッドがあれば放す。
                lock.notifyAll();
            }
        }

        /**
         * 止める相手が、監査の書き込みの直前に来たかを返す。
         *
         * @return 来たとき true
         */
        public boolean heldArrived() {
            synchronized (lock) {
                return heldArrived;
            }
        }

        /**
         * 止める相手を上限の時間で放したか（放す相手の書き込みが上限までに終わらず、入れ替えが起きなかった）を返す。
         *
         * @return 上限で放したとき true
         */
        public boolean releasedByTimeout() {
            synchronized (lock) {
                return releasedByTimeout;
            }
        }

        /** 準備をやめ、止めているスレッドがあれば放す。何度呼んでもよい。結果の値は次の準備まで残す。 */
        public void disarm() {
            synchronized (lock) {
                armed = false;
                held = null;
                releaser = null;
                lock.notifyAll();
            }
        }

        /** 監査の書き込みの時間を測る呼び出しのたびに通る。 */
        private void pass() {
            Thread current = Thread.currentThread();
            synchronized (lock) {
                if (!armed) {
                    return;
                }
                if (current == releaser) {
                    releaserCalls++;
                    if (releaserCalls == 2) {
                        released = true;
                        lock.notifyAll();
                    }
                    return;
                }
                if (current != held) {
                    return;
                }
                heldCalls++;
                if (heldCalls != 1) {
                    return;
                }
                heldArrived = true;
                awaitRelease();
            }
        }

        /** ロックを持った状態で呼ぶ。放す相手の書き込みの終わり・準備のやめ・次の準備・上限のどれかまで待つ。 */
        private void awaitRelease() {
            long myGeneration = generation;
            long deadline = System.nanoTime() + waitLimit.toNanos();
            while (!released && armed && generation == myGeneration) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    releasedByTimeout = true;
                    return;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    // 割り込みの印を戻して先へ進ませる（テストが止まらないように）。
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
