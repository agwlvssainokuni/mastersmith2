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

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 内部DB（{@code dataSource}）と対象DB（{@code targetDataSource}）の接続を貸す部品を {@link CountingDataSource} で包むテストだけの設定
 * （Step 18）。{@code BusinessDataConnectionUsageIT} だけが部品として足す。
 */
@TestConfiguration(proxyBeanMethods = false)
public class CountingDataSourceConfig {

    /**
     * 2つの接続を貸す部品を包む。
     *
     * @return 包む処理
     */
    @Bean
    public static BeanPostProcessor businessDataCountingWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource
                        && !(bean instanceof CountingDataSource)
                        && ("dataSource".equals(beanName) || "targetDataSource".equals(beanName))) {
                    return new CountingDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
