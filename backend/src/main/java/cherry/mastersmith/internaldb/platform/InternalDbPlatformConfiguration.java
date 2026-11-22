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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 内部DB の種類の判断・接続プールの設定・移行の置き場と失敗の記録の部品を組み立てる。
 *
 * <p>接続プールへの設定は {@link org.springframework.beans.factory.config.BeanPostProcessor} のため、静的な {@link Bean} メソッドで
 * 作る（設定クラスの早い作成を避ける）。種類の判断（{@link InternalDbSettings}）は、環境から 1 回だけ作る。
 */
@Configuration(proxyBeanMethods = false)
public class InternalDbPlatformConfiguration {

    /**
     * 内部DB の接続プールに、種類ごとの接続のたびの文を設定する後処理。
     *
     * @param environment Spring の環境
     * @return 後処理
     */
    @Bean
    public static InternalDbPoolCustomizer internalDbPoolCustomizer(Environment environment) {
        return new InternalDbPoolCustomizer(InternalDbSettings.from(environment));
    }

    /**
     * 内部DB の種類の判断の結果（起動時に 1 回）。
     *
     * @param environment Spring の環境
     * @return 判断の結果を持つ値
     */
    @Bean
    public InternalDbSettings internalDbSettings(Environment environment) {
        return InternalDbSettings.from(environment);
    }

    /**
     * Flyway に種類に応じた移行の置き場を渡す部品。
     *
     * @param settings 種類の判断の結果
     * @return Flyway の設定の差し替え
     */
    @Bean
    public InternalDbFlywayCustomizer internalDbFlywayCustomizer(InternalDbSettings settings) {
        return new InternalDbFlywayCustomizer(settings);
    }

    /**
     * 起動時の移行の入口（失敗の記録つき）。
     *
     * @return 移行の入口
     */
    @Bean
    public InternalDbMigrationStrategy internalDbMigrationStrategy() {
        return new InternalDbMigrationStrategy();
    }
}
