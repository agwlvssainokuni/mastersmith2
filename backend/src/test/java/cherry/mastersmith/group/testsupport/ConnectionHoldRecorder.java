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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 内部DB の接続を、スレッドごとに同時に何本借りているかの最大を記録するテストの補助（{@code scalability-design.md} 2.2 の
 * {@code GroupConnectionUsageIT}、計画の D-9）。本番のコードは変えず、テストだけで接続を貸す部品（{@link DataSource}）を包む。
 *
 * <p>要求は1つのスレッドで処理され、確定の後の監査の記録も同じスレッドで2本目を借りるため、スレッドごとの最大が「1つの要求が同時に
 * 持つ接続の数」になる。既存の {@code ConnectionAcquireCounter} は借りた回数だけで、同時の本数を数えないため足した。
 */
public final class ConnectionHoldRecorder {

    private final Map<Long, int[]> perThread = new ConcurrentHashMap<>();

    /** 記録を消す。 */
    public void reset() {
        perThread.clear();
    }

    /**
     * どのスレッドについても、同時に借りていた本数の最大を返す。
     *
     * @return 最大の本数（借りていなければ 0）
     */
    public int maxHeldByOneThread() {
        return perThread.values().stream().mapToInt(counts -> counts[1]).max().orElse(0);
    }

    private void acquired() {
        int[] counts = perThread.computeIfAbsent(Thread.currentThread().threadId(), id -> new int[2]);
        synchronized (counts) {
            counts[0]++;
            counts[1] = Math.max(counts[1], counts[0]);
        }
    }

    private void released(long threadId) {
        int[] counts = perThread.get(threadId);
        if (counts != null) {
            synchronized (counts) {
                counts[0]--;
            }
        }
    }

    private Connection track(Connection connection) {
        long threadId = Thread.currentThread().threadId();
        acquired();
        AtomicBoolean closed = new AtomicBoolean();
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName()) && closed.compareAndSet(false, true)) {
                released(threadId);
            }
            try {
                return method.invoke(connection, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (Connection) Proxy.newProxyInstance(
                ConnectionHoldRecorder.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    /** 接続を貸す部品を包む。 */
    private final class RecordingDataSource extends DelegatingDataSource {

        RecordingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return track(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return track(super.getConnection(username, password));
        }
    }

    /** テストで接続を貸す部品を包む設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * 記録の部品。
         *
         * @return 記録の部品
         */
        @Bean
        public static ConnectionHoldRecorder connectionHoldRecorder() {
            return new ConnectionHoldRecorder();
        }

        /**
         * 内部DB の接続を貸す部品を包む。
         *
         * @param recorder 記録の部品
         * @return 包む処理
         */
        @Bean
        public static BeanPostProcessor connectionHoldWrapper(ConnectionHoldRecorder recorder) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
                        return recorder.new RecordingDataSource(dataSource);
                    }
                    return bean;
                }
            };
        }
    }
}
