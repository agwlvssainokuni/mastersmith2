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
package cherry.mastersmith.internaldb.platform;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;

/**
 * 内部DB の接続プール（Bean の名前 {@code dataSource}）に、種類ごとの接続のたびの文を設定する（BR3.x・NFR4.1・NFR4.10・DD-1）。
 *
 * <p>当てる対象は Bean の名前 {@code dataSource} だけで決める（プールの名前は使わない）。対象DB の接続（{@code targetDataSource}・
 * {@code targetWriteDataSource}）には何もしない。
 *
 * <ul>
 *   <li>種類が PostgreSQL: {@link HikariDataSource} なら、接続のたびに待ちの上限（{@code lock_timeout}）を設定する。Hikari でない、
 *       またはプールが既に始まっているときは、黙って通さず {@link InternalDbStartupException}（{@code UNKNOWN}）で起動を止める。
 *   <li>種類が H2: 何もしない。
 *   <li>設定が空・対応しない種類: ERROR を 1 件記録して {@link InternalDbStartupException} で起動を止める。
 *   <li>{@code spring.datasource.url} が環境に無い: 種類の判断もプールへの設定もしない。
 * </ul>
 */
public class InternalDbPoolCustomizer implements BeanPostProcessor, PriorityOrdered {

    /** 内部DB の接続の Bean の名前。 */
    static final String DATA_SOURCE_BEAN_NAME = "dataSource";

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalDbPoolCustomizer.class);

    private final InternalDbSettings settings;

    /**
     * 種類の判断の結果から作る。
     *
     * @param settings 種類の判断の結果
     */
    public InternalDbPoolCustomizer(InternalDbSettings settings) {
        this.settings = settings;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!DATA_SOURCE_BEAN_NAME.equals(beanName) || settings.resolution().isEmpty()) {
            return bean;
        }
        InternalDbUrlResolution resolution = settings.resolution().get();
        if (!(resolution instanceof InternalDbUrlResolution.Supported supported)) {
            StartupFailureReport report = StartupFailureReporter.forResolution(resolution);
            StartupFailureReporter.log(report);
            throw new InternalDbStartupException(report.failureKind());
        }
        InternalDbKind kind = supported.kind();
        LOGGER.atInfo().addKeyValue("internalDbKind", kind.name()).log("Internal DB kind resolved");
        if (kind.connectionInitSql().isPresent()) {
            applyConnectionInitSql(bean, kind.connectionInitSql().get());
        }
        return bean;
    }

    private static void applyConnectionInitSql(Object bean, String sql) {
        if (bean instanceof HikariDataSource hikari && !hikari.isRunning()) {
            hikari.setConnectionInitSql(sql);
            return;
        }
        StartupFailureReport report = new StartupFailureReport(
                StartupFailureKind.UNKNOWN, List.of(bean.getClass().getName()), null, null, null, List.of());
        StartupFailureReporter.log(report);
        throw new InternalDbStartupException(report.failureKind());
    }
}
