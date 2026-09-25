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

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Spring を起動するすべてのテストで、内部DB の接続プール（HikariCP）の MBean の登録を既定で無効にする
 * （Intent 260925-storage-memory-fixes の計画 D8・Step 2）。
 *
 * <p>本番の設定（{@code application.yaml}）は、アプリを止めずに内部DB を詰め直すため、プールの MBean を登録する
 * （{@code spring.datasource.hikari.register-mbeans: true}）。テストでは、同じ JVM の中にキャッシュされた複数の Spring の文脈が
 * 同じプールの名前（{@code mastersmith-db}）で登録しようとし、HikariCP が「JMX name ... is already registered」を ERROR で出す。
 * そのため、テストの既定では登録しない。
 *
 * <p>テストのソースの中の {@code META-INF/spring.factories} で登録する。MBean の操作を確かめるテスト
 * （{@code H2CompactionByPoolSuspensionIT}）は、重ならないプールの名前を付けたうえで
 * {@code mastersmith.test-fixture.disable-hikari-mbeans=false} を渡し、本番の設定の値をそのまま使う。
 */
public class TestHikariMbeansEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** プールの MBean の登録の設定の名前。 */
    public static final String REGISTER_MBEANS = "spring.datasource.hikari.register-mbeans";

    /** 登録を無効にするかどうかの設定の名前（既定は無効にする）。 */
    public static final String DISABLE = "mastersmith.test-fixture.disable-hikari-mbeans";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(DISABLE, Boolean.class, true)) {
            return;
        }
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("testHikariMbeans", Map.of(REGISTER_MBEANS, "false")));
    }
}
