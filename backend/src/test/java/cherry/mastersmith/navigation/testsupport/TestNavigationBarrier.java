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
package cherry.mastersmith.navigation.testsupport;

import cherry.mastersmith.navigation.service.NavigationBarrier;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 業務のメニューの待ち合わせの口のテスト用の部品（{@code logical-components.md} の T2、{@code reliability-design.md} の 2節、NFR1.10）。
 *
 * <p>{@link #hold(Point)} で点に最初に来た1件を止め、{@link Gate#awaitArrival()} で止まった合図を受け、{@link Gate#release()} で
 * 放す。止める点を置いていないときは何もしない。待ちの上限 {@link #WAIT_LIMIT} は止まったままになったときの守りで、合否は応答の中身で
 * 決める（{@code sleep}・経過の時間・壁時計を使わない）。
 */
public class TestNavigationBarrier implements NavigationBarrier {

    /** 待ちの上限（テストの作りの誤りを見つけるための守り）。 */
    public static final Duration WAIT_LIMIT = Duration.ofSeconds(20);

    /** 待ち合わせの点。 */
    public enum Point {
        /** メニュー: {@code current()} の後、{@code snapshotFor} の前。 */
        AFTER_DSL_READ,
        /** 置き場: {@code resolve} の後、表示名の {@code current()} の前。 */
        AFTER_RESOLVE
    }

    /** 止める点（最初に来た1件だけを止める）。 */
    public static final class Gate {

        private final Point point;

        private final CountDownLatch arrived = new CountDownLatch(1);

        private final CountDownLatch released = new CountDownLatch(1);

        private Gate(Point point) {
            this.point = point;
        }

        /**
         * 止めた要求が点に来るまで待つ。
         *
         * @throws IllegalStateException 上限までに来なかったとき
         */
        public void awaitArrival() {
            await(arrived, "止める点に要求が来なかった: " + point);
        }

        /** 止めた要求を放す。 */
        public void release() {
            released.countDown();
        }
    }

    private final AtomicReference<Gate> gate = new AtomicReference<>();

    private final List<Gate> created = new CopyOnWriteArrayList<>();

    /**
     * 点に最初に来た1件を止める。
     *
     * @param point 点
     * @return 止める点
     */
    public Gate hold(Point point) {
        Gate armed = new Gate(point);
        created.add(armed);
        gate.set(armed);
        return armed;
    }

    /** 止める点を外し、止めている要求を放す。 */
    public void reset() {
        gate.set(null);
        created.forEach(Gate::release);
        created.clear();
    }

    @Override
    public void afterDslRead() {
        pass(Point.AFTER_DSL_READ);
    }

    @Override
    public void afterResolve() {
        pass(Point.AFTER_RESOLVE);
    }

    private void pass(Point point) {
        Gate current = gate.get();
        if (current != null && current.point == point && gate.compareAndSet(current, null)) {
            current.arrived.countDown();
            await(current.released, "止めた要求が放されなかった: " + point);
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }

    /** テストで待ち合わせの口を差し替える設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * テスト用の待ち合わせの口。
         *
         * @return 待ち合わせの口
         */
        @Bean
        @Primary
        public TestNavigationBarrier testNavigationBarrier() {
            return new TestNavigationBarrier();
        }
    }
}
