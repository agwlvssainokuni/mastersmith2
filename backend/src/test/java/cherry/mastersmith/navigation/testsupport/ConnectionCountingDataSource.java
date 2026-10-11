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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 内部DB の接続を、測る要求ごとに同時に何本借りたかの最大を数えるテストだけの包み（{@code logical-components.md} の T1、
 * {@code scalability-design.md} の 2節、NFR2.6）。本番のコードは変えない。
 *
 * <ul>
 *   <li>借りるたびにそのスレッドの借りている数を1増やし、返すと1減らす。{@link #reset()} の後に返った接続（{@code reset()} の時に借りて
 *       いたもの）で数を 0 未満に下げない（R-09 の (2)）
 *   <li>{@link MeasureIdFilter} が要求の始まりに印を {@link #begin(String)} で置き、{@code finally} で {@link #end()} で消す（R-09 の
 *       (1)）。印のある要求の処理中に借りた数の最大を印ごとに持ち、印の要求の始まりの借りている数も残す
 *   <li>{@link #reset()} は、借りている数・最大・印ごとの記録のすべてを消す（ログインの後、測る要求の直前に呼ぶ）
 * </ul>
 */
public final class ConnectionCountingDataSource extends DelegatingDataSource {

    private final Map<Long, int[]> inUseByThread = new ConcurrentHashMap<>();

    private final Map<String, Integer> peakByMeasure = new ConcurrentHashMap<>();

    private final Map<String, Integer> startByMeasure = new ConcurrentHashMap<>();

    private final ThreadLocal<String> measure = new ThreadLocal<>();

    /**
     * 包む。
     *
     * @param target 本物の接続を貸す部品
     */
    public ConnectionCountingDataSource(DataSource target) {
        super(target);
    }

    /** 借りている数・最大・印ごとの記録をすべて消す。 */
    public void reset() {
        inUseByThread.clear();
        peakByMeasure.clear();
        startByMeasure.clear();
    }

    /**
     * 今のスレッドで測る要求を始める（印を置く）。
     *
     * @param measureId 印
     */
    public void begin(String measureId) {
        measure.set(measureId);
        startByMeasure.put(measureId, inUse(Thread.currentThread().threadId()));
        peakByMeasure.putIfAbsent(measureId, 0);
    }

    /** 今のスレッドの測る要求を終える（印を消す）。 */
    public void end() {
        measure.remove();
    }

    /**
     * 印の要求の処理中に同時に借りた数の最大を返す。
     *
     * @param measureId 印
     * @return 最大（印の要求が来ていなければ空）
     */
    public Optional<Integer> peak(String measureId) {
        return Optional.ofNullable(peakByMeasure.get(measureId));
    }

    /**
     * 印の要求の始まりに、そのスレッドが借りていた数を返す。
     *
     * @param measureId 印
     * @return 借りていた数（印の要求が来ていなければ空）
     */
    public Optional<Integer> startInUse(String measureId) {
        return Optional.ofNullable(startByMeasure.get(measureId));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return track(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return track(super.getConnection(username, password));
    }

    private int inUse(long threadId) {
        int[] counts = inUseByThread.get(threadId);
        if (counts == null) {
            return 0;
        }
        synchronized (counts) {
            return counts[0];
        }
    }

    private Connection track(Connection connection) {
        long threadId = Thread.currentThread().threadId();
        int[] counts = inUseByThread.computeIfAbsent(threadId, id -> new int[1]);
        int now;
        synchronized (counts) {
            now = ++counts[0];
        }
        String measureId = measure.get();
        if (measureId != null) {
            peakByMeasure.merge(measureId, now, Math::max);
        }
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
                ConnectionCountingDataSource.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private void released(long threadId) {
        int[] counts = inUseByThread.get(threadId);
        if (counts != null) {
            synchronized (counts) {
                counts[0] = Math.max(0, counts[0] - 1);
            }
        }
    }
}
