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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cherry.mastersmith.audit.testsupport.AuditWriteReorderBarrierConfig.AuditWriteReorderBarrier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 監査の書き込みの順を決める待ち合わせ（{@link AuditWriteReorderBarrier}）の単体テスト（Intent 261016-business-data-views の U5、
 * 計画の Step 3、{@code unit-test-instructions.md} 4.1）。
 *
 * <p>スレッドの順は {@link CountDownLatch} とスレッドの状態で作り、{@code sleep} で順を作らない。上限で放す場合だけは短い上限
 * （200 ミリ秒）を渡し、経過の時間で放されるが、結果（放されたこと・印が立つこと）は決まっている。
 */
class AuditWriteReorderBarrierTest {

    /** 放されるはずの待ちの上限（テストの作りの誤りを見つけるための長めの値）。 */
    private static final Duration LONG_LIMIT = Duration.ofSeconds(10);

    private AuditWriteReorderBarrier barrier;

    /** 監査の受け取りが呼ぶ、書き込みの時間の測り方（差し替えた本物の口）。 */
    private LongSupplier timer;

    private final List<Thread> threads = new ArrayList<>();

    @BeforeEach
    void setUp() {
        AuditWriteReorderBarrierConfig config = new AuditWriteReorderBarrierConfig();
        barrier = config.auditWriteReorderBarrier();
        timer = config.reorderAuditWriteNanoTime(barrier);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        barrier.disarm();
        for (Thread thread : threads) {
            thread.join(LONG_LIMIT.toMillis());
        }
    }

    private Thread thread(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        threads.add(thread);
        return thread;
    }

    /** 止める相手の書き込みの直前と終わりの2回の呼び出しをし、終わったら知らせるスレッドを作る。 */
    private Thread heldWriter(CountDownLatch done) {
        return thread("held-writer", () -> {
            timer.getAsLong();
            timer.getAsLong();
            done.countDown();
        });
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(LONG_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("待ち合わせが上限までにそろわなかった");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("待ち合わせの途中で割り込まれた", e);
        }
    }

    /** 止める相手が書き込みの直前で止まり、待ちに入ったことを待つ。 */
    private void awaitHeldWaiting(Thread held) {
        await().atMost(LONG_LIMIT).until(() -> barrier.heldArrived() && held.getState() == Thread.State.TIMED_WAITING);
    }

    @Test
    @DisplayName("passes every call without stopping while not armed")
    void passesWhileNotArmed() {
        timer.getAsLong();
        timer.getAsLong();

        assertThat(barrier.heldArrived()).isFalse();
        assertThat(barrier.releasedByTimeout()).isFalse();
    }

    @Test
    @DisplayName("stops the held thread at its first call and releases it at the releaser's second call")
    void stopsHeldUntilReleaserFinishes() {
        CountDownLatch heldDone = new CountDownLatch(1);
        CountDownLatch releaserGo = new CountDownLatch(1);
        Thread held = heldWriter(heldDone);
        Thread releaser = thread("releaser-writer", () -> {
            awaitLatch(releaserGo);
            timer.getAsLong();
            timer.getAsLong();
        });
        barrier.armFor(held, releaser, LONG_LIMIT);
        held.start();
        releaser.start();
        awaitHeldWaiting(held);
        assertThat(heldDone.getCount()).isOne();

        releaserGo.countDown();

        awaitLatch(heldDone);
        assertThat(barrier.heldArrived()).isTrue();
        assertThat(barrier.releasedByTimeout()).isFalse();
    }

    @Test
    @DisplayName("does not release the held thread at the releaser's first call")
    void releaserFirstCallDoesNotRelease() throws InterruptedException {
        CountDownLatch heldDone = new CountDownLatch(1);
        CountDownLatch releaserFirstDone = new CountDownLatch(1);
        CountDownLatch releaserSecondGo = new CountDownLatch(1);
        Thread held = heldWriter(heldDone);
        Thread releaser = thread("releaser-writer", () -> {
            timer.getAsLong();
            releaserFirstDone.countDown();
            awaitLatch(releaserSecondGo);
            timer.getAsLong();
        });
        barrier.armFor(held, releaser, LONG_LIMIT);
        held.start();
        awaitHeldWaiting(held);

        releaser.start();
        awaitLatch(releaserFirstDone);

        // 放す印はロックの中で同期して立つため、1回目の呼び出しが戻った時点で止める相手が待ちのままなら放されていない。
        assertThat(held.getState()).isEqualTo(Thread.State.TIMED_WAITING);
        assertThat(heldDone.getCount()).isOne();
        releaserSecondGo.countDown();
        assertThat(heldDone.await(LONG_LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(barrier.releasedByTimeout()).isFalse();
    }

    @Test
    @DisplayName("does not release the held thread on calls from another thread")
    void otherThreadDoesNotRelease() throws InterruptedException {
        CountDownLatch heldDone = new CountDownLatch(1);
        Thread held = heldWriter(heldDone);
        Thread releaser = thread("releaser-not-started", () -> {});
        Thread other = thread("other-writer", () -> {
            timer.getAsLong();
            timer.getAsLong();
        });
        barrier.armFor(held, releaser, LONG_LIMIT);
        held.start();
        awaitHeldWaiting(held);

        other.start();
        other.join(LONG_LIMIT.toMillis());

        assertThat(other.isAlive()).isFalse();
        assertThat(held.getState()).isEqualTo(Thread.State.TIMED_WAITING);
        assertThat(heldDone.getCount()).isOne();
        assertThat(barrier.releasedByTimeout()).isFalse();
    }

    @Test
    @DisplayName("passes the held thread without stopping when the releaser has already finished")
    void passesWhenReleaserFinishedFirst() throws InterruptedException {
        Thread releaser = thread("releaser-writer", () -> {
            timer.getAsLong();
            timer.getAsLong();
        });
        barrier.armFor(Thread.currentThread(), releaser, LONG_LIMIT);
        releaser.start();
        releaser.join(LONG_LIMIT.toMillis());

        long started = System.nanoTime();
        timer.getAsLong();
        timer.getAsLong();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(LONG_LIMIT);
        assertThat(barrier.heldArrived()).isTrue();
        assertThat(barrier.releasedByTimeout()).isFalse();
    }

    @Test
    @DisplayName("releases the held thread at the wait limit and marks releasedByTimeout")
    void releasesAtWaitLimit() {
        Thread releaser = thread("releaser-not-started", () -> {});
        barrier.armFor(Thread.currentThread(), releaser, Duration.ofMillis(200));

        timer.getAsLong();

        assertThat(barrier.heldArrived()).isTrue();
        assertThat(barrier.releasedByTimeout()).isTrue();
    }

    @Test
    @DisplayName("disarm releases the held thread and armFor clears the previous results")
    void disarmReleasesAndArmResets() {
        CountDownLatch heldDone = new CountDownLatch(1);
        Thread held = heldWriter(heldDone);
        Thread releaser = thread("releaser-not-started", () -> {});
        barrier.armFor(held, releaser, LONG_LIMIT);
        held.start();
        awaitHeldWaiting(held);

        barrier.disarm();
        barrier.disarm();

        awaitLatch(heldDone);
        assertThat(barrier.heldArrived()).isTrue();
        assertThat(barrier.releasedByTimeout()).isFalse();

        barrier.armFor(thread("next-held", () -> {}), thread("next-releaser", () -> {}), LONG_LIMIT);
        assertThat(barrier.heldArrived()).isFalse();
        assertThat(barrier.releasedByTimeout()).isFalse();
    }
}
