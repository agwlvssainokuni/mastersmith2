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

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;

/**
 * 内部DB の種類に応じた移行の置き場を Flyway に渡す（BR2.2）。種類が決まらないとき（環境に接続先が無い・空・対応しない）は何も
 * しない（設定の {@code spring.flyway.locations} のまま。空・対応しない種類は {@link InternalDbPoolCustomizer} が先に起動を止める）。
 */
public class InternalDbFlywayCustomizer implements FlywayConfigurationCustomizer {

    private final InternalDbSettings settings;

    /**
     * 種類の判断の結果から作る。
     *
     * @param settings 種類の判断の結果
     */
    public InternalDbFlywayCustomizer(InternalDbSettings settings) {
        this.settings = settings;
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        settings.kind().ifPresent(kind -> configuration.locations(kind.migrationLocation()));
    }
}
