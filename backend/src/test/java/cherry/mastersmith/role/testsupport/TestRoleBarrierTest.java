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
package cherry.mastersmith.role.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Gate;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Point;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Signal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ロールの待ち合わせの口のスレッドの記録（{@link Gate#arrivedThread()}・{@link Signal#passedThread()}）の単体テスト（Intent
 * 261016-business-data-views の U5、計画の Step 3）。
 */
class TestRoleBarrierTest {

    private static final long ROLE_ID = 42L;

    private static final String KEY = String.valueOf(ROLE_ID);

    private TestRoleBarrier barrier;

    @BeforeEach
    void setUp() {
        barrier = new TestRoleBarrier();
    }

    @AfterEach
    void tearDown() {
        barrier.reset();
    }

    @Test
    @DisplayName("Gate.arrivedThread is the thread stopped at the point")
    void gateRecordsStoppedThread() throws InterruptedException {
        Gate gate = barrier.hold(Point.AFTER_LOCK, RoleOperation.CHANGE_PERMISSIONS, KEY);
        Thread saving = new Thread(() -> barrier.afterLock(RoleOperation.CHANGE_PERMISSIONS, ROLE_ID), "saving");
        saving.start();

        gate.awaitArrival();

        assertThat(gate.arrivedThread()).isSameAs(saving);
        gate.release();
        saving.join(TestRoleBarrier.WAIT_LIMIT.toMillis());
        assertThat(saving.isAlive()).isFalse();
    }

    @Test
    @DisplayName("Signal.passedThread is the first thread and does not change when another thread passes")
    void signalRecordsFirstThread() throws InterruptedException {
        Signal signal = barrier.signal(Point.BEFORE_LOCK, RoleOperation.CHANGE_PERMISSIONS, KEY);
        Thread first = new Thread(() -> barrier.beforeLock(RoleOperation.CHANGE_PERMISSIONS, ROLE_ID), "first");
        Thread second = new Thread(() -> barrier.beforeLock(RoleOperation.CHANGE_PERMISSIONS, ROLE_ID), "second");

        first.start();
        first.join(TestRoleBarrier.WAIT_LIMIT.toMillis());
        signal.awaitPassed();
        second.start();
        second.join(TestRoleBarrier.WAIT_LIMIT.toMillis());

        assertThat(signal.passedThread()).isSameAs(first);
    }

    @Test
    @DisplayName("arrivedThread and passedThread are null before anything comes to the point")
    void nullBeforeArrival() {
        Gate gate = barrier.hold(Point.AFTER_LOCK, RoleOperation.CHANGE_PERMISSIONS, KEY);
        Signal signal = barrier.signal(Point.BEFORE_LOCK, RoleOperation.CHANGE_PERMISSIONS, KEY);

        // 別の鍵・別の点の呼び出しは記録しない。
        barrier.beforeLock(RoleOperation.CHANGE_PERMISSIONS, ROLE_ID + 1);
        barrier.afterCheck(RoleOperation.CHANGE_PERMISSIONS, KEY);

        assertThat(gate.arrivedThread()).isNull();
        assertThat(signal.passedThread()).isNull();
    }
}
