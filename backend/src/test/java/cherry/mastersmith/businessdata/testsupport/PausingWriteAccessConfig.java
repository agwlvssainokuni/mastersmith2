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

import cherry.mastersmith.targetdb.service.TargetWriteAccess;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 書き込みの口の Bean（{@code jdbcTargetWriteAccess}）を {@link PausingTargetWriteAccess} で包むテストだけの設定（Intent
 * 261029-reference-lookup-cleanup の U2、reliability-design.md 4.1）。{@code BusinessDataTestApp.start(..., sources, ...)} で足す。
 * 本番の注入は interface の型だけのため、本番のクラスを変えない。
 */
@TestConfiguration(proxyBeanMethods = false)
public class PausingWriteAccessConfig {

    /** 包む Bean の名前。 */
    public static final String BEAN = "jdbcTargetWriteAccess";

    /**
     * 書き込みの口を包む。
     *
     * @return 包む処理
     */
    @Bean
    public static BeanPostProcessor businessDataPausingWriteWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (BEAN.equals(beanName)
                        && bean instanceof TargetWriteAccess access
                        && !(bean instanceof PausingTargetWriteAccess)) {
                    return new PausingTargetWriteAccess(access);
                }
                return bean;
            }
        };
    }
}
