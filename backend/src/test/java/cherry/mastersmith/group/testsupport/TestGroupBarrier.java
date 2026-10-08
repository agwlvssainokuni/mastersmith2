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
package cherry.mastersmith.group.testsupport;

import cherry.mastersmith.group.domain.GroupOperation;
import cherry.mastersmith.group.service.GroupBarrier;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * グループの操作の待ち合わせの口のテスト用の部品（{@code reliability-design.md} 2.1 の4つの点・4.2、{@code team.md} の「重なりは
 * スレッドの数に頼らず待ち合わせで確実に作る」）。
 *
 * <ul>
 *   <li>{@link #hold(Point, GroupOperation, String)}: 点に最初に来た1件を止め、{@link Gate#release()} まで放さない
 *   <li>{@link #signal(Point, GroupOperation, String)}: 点を通ったことを知らせる（止めない）
 * </ul>
 *
 * <p>どの待ちにも上限 {@link #WAIT_LIMIT} を置き、上限に届いたらテストの作りの誤りとして例外にする。排他の前後の点の鍵はグループの ID
 * の文字列、ほかの点の鍵は {@link GroupBarrier} の決まり（名前の鍵、{@code <グループの ID>:<利用者 ID>}）。
 */
public class TestGroupBarrier implements GroupBarrier {

    /** 待ちの上限（テストの作りの誤りを見つけるための長めの値）。 */
    public static final Duration WAIT_LIMIT = Duration.ofSeconds(20);

    /** 待ち合わせの点。 */
    public enum Point {
        /** 排他の直前。 */
        BEFORE_LOCK,
        /** 排他の直後。 */
        AFTER_LOCK,
        /** 判定の後、書き込みの前。 */
        AFTER_CHECK,
        /** 書き込みと flush の後、確定の前。 */
        AFTER_WRITE
    }

    /** 止める点（最初に来た1件だけを止める）。 */
    public static final class Gate {

        private final Point point;

        private final GroupOperation operation;

        private final String key;

        private final AtomicBoolean taken = new AtomicBoolean();

        private final CountDownLatch arrived = new CountDownLatch(1);

        private final CountDownLatch released = new CountDownLatch(1);

        private Gate(Point point, GroupOperation operation, String key) {
            this.point = point;
            this.operation = operation;
            this.key = key;
        }

        /**
         * 止めた操作が点に来るまで待つ。
         *
         * @throws IllegalStateException 上限までに来なかったとき
         */
        public void awaitArrival() {
            await(arrived, "止める点に操作が来なかった: " + point + " " + operation + " " + key);
        }

        /** 止めた操作を放す。 */
        public void release() {
            released.countDown();
        }

        private boolean matches(Point point, GroupOperation operation, String key) {
            return this.point == point && this.operation == operation && this.key.equals(key);
        }
    }

    /** 通ったことの知らせ。 */
    public static final class Signal {

        private final Point point;

        private final GroupOperation operation;

        private final String key;

        private final CountDownLatch passed = new CountDownLatch(1);

        private Signal(Point point, GroupOperation operation, String key) {
            this.point = point;
            this.operation = operation;
            this.key = key;
        }

        /**
         * 点を通るまで待つ。
         *
         * @throws IllegalStateException 上限までに通らなかったとき
         */
        public void awaitPassed() {
            await(passed, "点を通らなかった: " + point + " " + operation + " " + key);
        }

        private boolean matches(Point point, GroupOperation operation, String key) {
            return this.point == point && this.operation == operation && this.key.equals(key);
        }
    }

    private final List<Gate> gates = new CopyOnWriteArrayList<>();

    private final List<Signal> signals = new CopyOnWriteArrayList<>();

    /**
     * 点に最初に来た1件を止める。
     *
     * @param point 点
     * @param operation 操作の区分
     * @param key 鍵
     * @return 止める点
     */
    public Gate hold(Point point, GroupOperation operation, String key) {
        Gate gate = new Gate(point, operation, key);
        gates.add(gate);
        return gate;
    }

    /**
     * 点を通ったことを知らせる。
     *
     * @param point 点
     * @param operation 操作の区分
     * @param key 鍵
     * @return 知らせ
     */
    public Signal signal(Point point, GroupOperation operation, String key) {
        Signal signal = new Signal(point, operation, key);
        signals.add(signal);
        return signal;
    }

    /** すべての止める点を放し、設定を消す。 */
    public void reset() {
        gates.forEach(Gate::release);
        gates.clear();
        signals.clear();
    }

    @Override
    public void beforeLock(GroupOperation operation, long groupId) {
        pass(Point.BEFORE_LOCK, operation, String.valueOf(groupId));
    }

    @Override
    public void afterLock(GroupOperation operation, long groupId) {
        pass(Point.AFTER_LOCK, operation, String.valueOf(groupId));
    }

    @Override
    public void afterCheck(GroupOperation operation, String key) {
        pass(Point.AFTER_CHECK, operation, key);
    }

    @Override
    public void afterWrite(GroupOperation operation, String key) {
        pass(Point.AFTER_WRITE, operation, key);
    }

    private void pass(Point point, GroupOperation operation, String key) {
        for (Signal signal : signals) {
            if (signal.matches(point, operation, key)) {
                signal.passed.countDown();
            }
        }
        for (Gate gate : gates) {
            if (gate.matches(point, operation, key) && gate.taken.compareAndSet(false, true)) {
                gate.arrived.countDown();
                await(gate.released, "止めた操作が放されなかった: " + point + " " + operation + " " + key);
                return;
            }
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

    /** テストで差し替える設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * 本番の何もしない待ち合わせの口の代わりに使う。
         *
         * @return 待ち合わせの口
         */
        @Bean
        @Primary
        public TestGroupBarrier testGroupBarrier() {
            return new TestGroupBarrier();
        }
    }
}
