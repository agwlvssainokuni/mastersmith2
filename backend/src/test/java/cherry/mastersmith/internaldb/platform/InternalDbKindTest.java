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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 種類ごとの設定（T7）と、Flyway の移行の置き場の差し替え（BR2.2）。 */
class InternalDbKindTest {

    @Test
    @DisplayName("H2 uses the original migration location and has no per-connection statement")
    void h2Settings() {
        assertThat(InternalDbKind.H2.urlPrefix()).isEqualTo("jdbc:h2:");
        assertThat(InternalDbKind.H2.migrationLocation()).isEqualTo("classpath:db/migration");
        assertThat(InternalDbKind.H2.connectionInitSql()).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("PostgreSQL uses its own migration location and sets lock_timeout to 2 seconds per connection")
    void postgresSettings() {
        assertThat(InternalDbKind.POSTGRESQL.urlPrefix()).isEqualTo("jdbc:postgresql:");
        assertThat(InternalDbKind.POSTGRESQL.migrationLocation()).isEqualTo("classpath:db/migration-postgresql");
        assertThat(InternalDbKind.POSTGRESQL.connectionInitSql()).contains("SET lock_timeout = '2s'");
    }

    @Test
    @DisplayName("the Flyway customizer passes the PostgreSQL location only for PostgreSQL")
    void flywayCustomizerPicksTheLocation() {
        FluentConfiguration postgres = new FluentConfiguration();
        new InternalDbFlywayCustomizer(settingsOf(InternalDbKind.POSTGRESQL)).customize(postgres);
        FluentConfiguration h2 = new FluentConfiguration();
        new InternalDbFlywayCustomizer(settingsOf(InternalDbKind.H2)).customize(h2);

        assertThat(postgres.getLocations())
                .extracting(Object::toString)
                .containsExactly("classpath:db/migration-postgresql");
        assertThat(h2.getLocations()).extracting(Object::toString).containsExactly("classpath:db/migration");
    }

    @Test
    @DisplayName("the Flyway customizer leaves the configuration alone when no kind is resolved")
    void flywayCustomizerDoesNothingWithoutAKind() {
        FluentConfiguration untouched = new FluentConfiguration();
        String before = untouched.getLocations()[0].toString();

        new InternalDbFlywayCustomizer(new InternalDbSettings(Optional.empty())).customize(untouched);
        new InternalDbFlywayCustomizer(new InternalDbSettings(Optional.of(new InternalDbUrlResolution.Unsupported())))
                .customize(untouched);

        assertThat(untouched.getLocations()).extracting(Object::toString).containsExactly(before);
    }

    private static InternalDbSettings settingsOf(InternalDbKind kind) {
        return new InternalDbSettings(Optional.of(new InternalDbUrlResolution.Supported(kind)));
    }
}
