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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestHikariMbeansEnvironmentPostProcessor;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * アプリを止めずに内部DB を詰め直すための HikariCP の設定の確かめ（Intent 260925-storage-memory-fixes の FR1.1）。
 *
 * <p>運用の道具（{@code docker/hikari-pool.sh}）は、プールの MBean の標準の操作（一時停止・接続の破棄・再開）と、接続先の
 * {@code DEFRAG_ALWAYS=TRUE} に頼る。どれかの設定が消えると詰め直せなくなるため、{@code application.yaml} の値を確かめる。
 * あわせて、テストの既定で MBean の登録を無効にする補助（{@link TestHikariMbeansEnvironmentPostProcessor}）の動きを確かめる。
 */
class HikariJmxSettingsTest {

    private MockEnvironment environment;

    @BeforeEach
    void loadApplicationYaml() throws IOException {
        environment = new MockEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));
    }

    @Test
    @DisplayName("application.yaml registers the pool MBeans and allows pool suspension")
    void registersMbeansAndAllowsSuspension() {
        assertThat(environment.getProperty("spring.datasource.hikari.register-mbeans", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.datasource.hikari.allow-pool-suspension", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("the pool name in the MBean name stays mastersmith-db and the default URL sets DEFRAG_ALWAYS=TRUE")
    void poolNameAndDefragAlways() {
        // 運用の道具は MBean の名前 com.zaxxer.hikari:type=Pool (mastersmith-db) を使う。
        assertThat(environment.getProperty("spring.datasource.hikari.pool-name"))
                .isEqualTo("mastersmith-db");
        assertThat(environment.getProperty("spring.datasource.url")).containsIgnoringCase("DEFRAG_ALWAYS=TRUE");
    }

    @Test
    @DisplayName("the test fixture disables MBean registration by default")
    void testFixtureDisablesMbeansByDefault() {
        new TestHikariMbeansEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(TestHikariMbeansEnvironmentPostProcessor.REGISTER_MBEANS, Boolean.class))
                .isFalse();
    }

    @Test
    @DisplayName("the test fixture keeps the application setting when it is switched off")
    void testFixtureCanBeSwitchedOff() {
        environment.setProperty(TestHikariMbeansEnvironmentPostProcessor.DISABLE, "false");

        new TestHikariMbeansEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(TestHikariMbeansEnvironmentPostProcessor.REGISTER_MBEANS, Boolean.class))
                .isTrue();
    }
}
