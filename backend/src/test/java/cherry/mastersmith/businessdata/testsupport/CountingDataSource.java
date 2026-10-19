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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 接続を借りた回数・同時に借りた数の最大と、借りたときに相手の DataSource が借りていた数の最大を数えるテストだけの包み（Step 18、
 * 既存の {@code navigation/testsupport/ConnectionCountingDataSource} と同じ形を、内部DB と対象DB の2つの組で使う）。本番のコードは
 * 変えない。
 */
public final class CountingDataSource extends DelegatingDataSource {

    private final AtomicInteger inUse = new AtomicInteger();

    private final AtomicInteger peak = new AtomicInteger();

    private final AtomicInteger borrows = new AtomicInteger();

    private final AtomicInteger peerInUseAtBorrow = new AtomicInteger();

    private volatile CountingDataSource peer;

    /**
     * 包む。
     *
     * @param target 本物の接続を貸す部品
     */
    public CountingDataSource(DataSource target) {
        super(target);
    }

    /**
     * 相手（内部DB に対する対象DB、またはその逆）を決める。
     *
     * @param other 相手
     */
    public void pairWith(CountingDataSource other) {
        this.peer = other;
        other.peer = this;
    }

    /** 回数と最大を消す（今借りている数は残す）。 */
    public void reset() {
        peak.set(inUse.get());
        borrows.set(0);
        peerInUseAtBorrow.set(0);
    }

    /**
     * 今借りている数を返す。
     *
     * @return 数
     */
    public int inUse() {
        return inUse.get();
    }

    /**
     * 同時に借りた数の最大を返す。
     *
     * @return 最大
     */
    public int peak() {
        return peak.get();
    }

    /**
     * 借りた回数を返す。
     *
     * @return 回数
     */
    public int borrows() {
        return borrows.get();
    }

    /**
     * 借りたときに相手が借りていた数の最大を返す。
     *
     * @return 最大
     */
    public int peerInUseAtBorrow() {
        return peerInUseAtBorrow.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return track(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return track(super.getConnection(username, password));
    }

    private Connection track(Connection connection) {
        borrows.incrementAndGet();
        int now = inUse.incrementAndGet();
        peak.accumulateAndGet(now, Math::max);
        CountingDataSource other = peer;
        if (other != null) {
            peerInUseAtBorrow.accumulateAndGet(other.inUse(), Math::max);
        }
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close") && closed.compareAndSet(false, true)) {
                        inUse.decrementAndGet();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
