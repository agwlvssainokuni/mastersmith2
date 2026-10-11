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

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 接続の数えの部品をかぶせるテストだけの設定（{@code logical-components.md} の T1、計画の 2.2 の R-09 の (3)）。
 * {@code NavigationConnectionUsageIT} だけが {@code @Import} する。
 *
 * <ul>
 *   <li>本物の {@code dataSource} を {@link ConnectionCountingDataSource} で包む（{@link BeanPostProcessor}）
 *   <li>{@link MeasureIdFilter} を最も高い順で登録する（認証のフィルターより前に印を置く）
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConnectionCountingConfig {

    /**
     * 包んだ接続を貸す部品から、数えの包みを取り出す。
     *
     * @param dataSource 接続を貸す部品（この設定で包んだもの）
     * @return 数えの包み
     * @throws IllegalStateException 包まれていないとき（テストの用意の誤り）
     */
    public static ConnectionCountingDataSource counter(DataSource dataSource) {
        if (dataSource instanceof ConnectionCountingDataSource counting) {
            return counting;
        }
        throw new IllegalStateException("接続を貸す部品が数えの包みで包まれていません");
    }

    /**
     * 内部DB の接続を貸す部品を包む。
     *
     * @return 包む処理
     */
    @Bean
    public static BeanPostProcessor connectionCountingWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource
                        && !(bean instanceof ConnectionCountingDataSource)
                        && "dataSource".equals(beanName)) {
                    return new ConnectionCountingDataSource(dataSource);
                }
                return bean;
            }
        };
    }

    /**
     * 測る要求に印を付けるフィルターを登録する。
     *
     * @param dataSource 接続を貸す部品（包んだもの）
     * @return 登録
     */
    @Bean
    public FilterRegistrationBean<MeasureIdFilter> measureIdFilter(DataSource dataSource) {
        FilterRegistrationBean<MeasureIdFilter> registration =
                new FilterRegistrationBean<>(new MeasureIdFilter(counter(dataSource)));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
