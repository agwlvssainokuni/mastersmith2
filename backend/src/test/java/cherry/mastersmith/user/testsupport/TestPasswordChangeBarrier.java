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
package cherry.mastersmith.user.testsupport;

import cherry.mastersmith.user.service.PasswordChangeBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * パスワードの変更の、照合の後・書き込みの前に、別の操作を別のスレッドで確定させるテストの待ち合わせの口
 * （{@code reliability-design.md} 2節の同時の変更の確かめ）。
 *
 * <p>{@link #runOnce(Runnable)} で渡した操作を、次の1回の {@code beforeWrite} で別のスレッドに渡して終わるまで待つ（上限 20 秒。
 * {@code sleep} に頼らない）。別のスレッドの中で起きた {@code beforeWrite} は何もしない。
 */
public class TestPasswordChangeBarrier implements PasswordChangeBarrier {

    /** 別のスレッドの操作を待つ上限（秒）。 */
    static final long TIMEOUT_SECONDS = 20;

    private final AtomicReference<Runnable> pending = new AtomicReference<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 次の1回の書き込みの前に、別のスレッドで行う操作を決める。
     *
     * @param action 操作
     */
    public void runOnce(Runnable action) {
        pending.set(action);
    }

    @Override
    public void beforeWrite(long userId) {
        Runnable action = pending.getAndSet(null);
        if (action == null) {
            return;
        }
        Future<?> done = executor.submit(action);
        try {
            done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("待ち合わせの間の操作が終わらなかった", e);
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
        public TestPasswordChangeBarrier testPasswordChangeBarrier() {
            return new TestPasswordChangeBarrier();
        }
    }
}
