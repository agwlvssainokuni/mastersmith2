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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 監査の書き込みの直前で、同時の要求を指定した件数がそろうまで待ち合わせるテストの補助（F2 の再現。FR3.4）。
 *
 * <p>監査の受け取り（{@code AuditEventListener}）は、確定の後に同じスレッドで、書き込みの時間を測る呼び出し
 * （{@link LongSupplier}）の直後に新しいトランザクションで2本目の接続を借りる。この呼び出しを差し替えて待ち合わせると、
 * そろった時点の各要求は業務の接続（1本目）を持ったままになり、同時の要求がプールの上限以上なら2本目の待ちが必ず起きる。
 * 実時刻の {@code sleep} は使わない。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuditWriteBarrierConfig {

    /**
     * 待ち合わせの仕組みを置く。
     *
     * @return 待ち合わせの仕組み
     */
    @Bean
    public AuditWriteBarrier auditWriteBarrier() {
        return new AuditWriteBarrier();
    }

    /**
     * 書き込みの時間の測り方を、待ち合わせを挟むものに差し替える。
     *
     * @param barrier 待ち合わせの仕組み
     * @return 経過時間の測り方
     */
    @Bean
    @Primary
    public LongSupplier barrierAuditWriteNanoTime(AuditWriteBarrier barrier) {
        return () -> {
            barrier.arrive();
            return System.nanoTime();
        };
    }

    /**
     * 同時の要求を、監査の書き込みの直前で待ち合わせる仕組み。
     *
     * <p>{@link #arm(int, Duration)} で件数と待ちの上限を決めると、各スレッドの最初の呼び出しだけが待ち合わせに加わる
     * （書き込みの終わりの時刻を測る2回目の呼び出しは待たない）。上限までにそろわなかったときは、テストが止まらないよう、
     * そのまま先へ進ませる。{@link #disarm()} の後は待たない。
     */
    public static final class AuditWriteBarrier {

        private volatile CountDownLatch latch;

        private volatile Duration timeout = Duration.ZERO;

        private final Set<Thread> arrived = ConcurrentHashMap.newKeySet();

        /**
         * 待ち合わせを始める。
         *
         * @param parties そろえる要求の件数（1以上）
         * @param maxWait 待ちの上限
         */
        public void arm(int parties, Duration maxWait) {
            if (parties < 1) {
                throw new IllegalArgumentException("そろえる件数は1以上にしてください: " + parties);
            }
            arrived.clear();
            timeout = maxWait;
            latch = new CountDownLatch(parties);
        }

        /** 待ち合わせをやめる。 */
        public void disarm() {
            CountDownLatch current = latch;
            latch = null;
            if (current != null) {
                // 待っているスレッドがあれば放す。
                while (current.getCount() > 0) {
                    current.countDown();
                }
            }
            arrived.clear();
        }

        /**
         * 待ち合わせにそろった件数を返す。
         *
         * @return そろった件数
         */
        public int arrivedCount() {
            return arrived.size();
        }

        /**
         * 前回の待ち合わせで、決めた件数がすべてそろったかを返す。
         *
         * @return すべてそろったとき true
         */
        public boolean allArrived() {
            CountDownLatch current = latch;
            return current != null && current.getCount() == 0;
        }

        private void arrive() {
            CountDownLatch current = latch;
            if (current == null || !arrived.add(Thread.currentThread())) {
                return;
            }
            current.countDown();
            try {
                // 上限までにそろわなくても先へ進ませる（そろったかどうかは allArrived で分かる）。
                current.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
