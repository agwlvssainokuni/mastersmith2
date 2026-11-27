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
package cherry.mastersmith.common.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 内部DB の {@link DataSource}（Bean の名前 {@code dataSource}）が流した文の数と、接続を借りた回数を数えるテストの補助
 * （Intent 261114-internal-db-postgresql の U2。1 回の保存で文が何本流れ、接続を何回借りるかを確かめる）。
 *
 * <p>{@link java.lang.reflect.Proxy} で {@link DataSource}・{@link Connection}・{@link PreparedStatement} を包む。{@code unwrap}・
 * {@code isWrapperFor} は元の {@link DataSource} へ通す（接続プールの指標の後処理が元に届くように、この後処理は最後に動く）。
 * 本体の Bean の定義は変えない。{@code @Import(StatementCounter.Config.class)} で使う。対象DB の {@code DataSource} などは包まない。
 */
public final class StatementCounter implements BeanPostProcessor, Ordered {

    /** 内部DB の {@link DataSource} の Bean の名前。 */
    public static final String DATA_SOURCE_BEAN = "dataSource";

    /** 数える {@link PreparedStatement} の実行のメソッド。 */
    private static final Set<String> EXECUTIONS = Set.of(
            "execute", "executeUpdate", "executeQuery", "executeBatch", "executeLargeUpdate", "executeLargeBatch");

    private final AtomicLong statements = new AtomicLong();

    private final AtomicLong connections = new AtomicLong();

    /** 数えを使うテストの設定（{@code @Import} で取り込む）。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * 数えを作る（{@link BeanPostProcessor} のため static）。
         *
         * @return 数え
         */
        @Bean
        public static StatementCounter statementCounter() {
            return new StatementCounter();
        }
    }

    /**
     * これまでに流れた文の本数を返す。
     *
     * @return 本数
     */
    public long statements() {
        return statements.get();
    }

    /**
     * これまでに接続を借りた回数を返す。
     *
     * @return 回数
     */
    public long connections() {
        return connections.get();
    }

    /** 数えを 0 に戻す。 */
    public void reset() {
        statements.set(0);
        connections.set(0);
    }

    /**
     * 渡した {@link DataSource} を包む（Spring を使わずに数え方を確かめるための口）。
     *
     * @param dataSource 元の {@link DataSource}
     * @return 数える {@link DataSource}
     */
    public DataSource wrap(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return proxy(DataSource.class, dataSource, (method, target, args) -> {
            Object result = invoke(method, target, args);
            if ("getConnection".equals(method.getName())) {
                connections.incrementAndGet();
                return wrapConnection((Connection) result);
            }
            return result;
        });
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (DATA_SOURCE_BEAN.equals(beanName) && bean instanceof DataSource dataSource) {
            return wrap(dataSource);
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private Connection wrapConnection(Connection connection) {
        return proxy(Connection.class, connection, (method, target, args) -> {
            Object result = invoke(method, target, args);
            if ("prepareStatement".equals(method.getName()) && result instanceof PreparedStatement statement) {
                return wrapStatement(statement);
            }
            return result;
        });
    }

    private PreparedStatement wrapStatement(PreparedStatement statement) {
        return proxy(PreparedStatement.class, statement, (method, target, args) -> {
            if (EXECUTIONS.contains(method.getName())) {
                statements.incrementAndGet();
            }
            return invoke(method, target, args);
        });
    }

    /** 元の呼び出しの結果。 */
    @FunctionalInterface
    private interface Interceptor {
        Object intercept(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, T target, Interceptor interceptor) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> type.getSimpleName() + " counting proxy";
                };
            }
            return interceptor.intercept(method, target, args);
        };
        return (T) Proxy.newProxyInstance(StatementCounter.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
