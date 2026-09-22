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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.user.service.PasswordProperties;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Java の設定の型の既定値と、application.yaml の {@code ${環境変数:既定値}} の既定値がそろっていることを確かめる（計画の C1）。 */
class AuthPropertiesDefaultsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([A-Z0-9_]+):(.*)}$");

    private static PropertySource<?> yaml() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        return sources.getFirst();
    }

    private static String yamlDefault(String name) throws IOException {
        Object raw = yaml().getProperty(name);
        Matcher matcher = PLACEHOLDER.matcher(String.valueOf(raw));
        assertThat(matcher.matches()).as("%s は ${環境変数:既定値} の形", name).isTrue();
        return matcher.group(2);
    }

    private static String javaDefault(Class<?> type, String component) {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        for (Parameter parameter : constructor.getParameters()) {
            if (parameter.getName().equals(component)) {
                String[] values = parameter.getAnnotation(DefaultValue.class).value();
                return values.length == 0 ? "" : values[0];
            }
        }
        throw new AssertionError(component);
    }

    @Test
    @DisplayName("token lifetimes agree between Java and application.yaml")
    void tokenLifetimes() throws IOException {
        assertThat(yamlDefault("mastersmith.auth.access-token-ttl"))
                .isEqualTo(javaDefault(AuthProperties.class, "accessTokenTtl"))
                .isEqualTo("5m");
        assertThat(yamlDefault("mastersmith.auth.refresh-token-ttl"))
                .isEqualTo(javaDefault(AuthProperties.class, "refreshTokenTtl"))
                .isEqualTo("24h");
    }

    @Test
    @DisplayName("lock settings agree between Java and application.yaml")
    void lock() throws IOException {
        assertThat(yamlDefault("mastersmith.auth.lock.threshold"))
                .isEqualTo(javaDefault(AuthProperties.Lock.class, "threshold"))
                .isEqualTo("5");
        assertThat(yamlDefault("mastersmith.auth.lock.duration"))
                .isEqualTo(javaDefault(AuthProperties.Lock.class, "duration"))
                .isEqualTo("30m");
    }

    @Test
    @DisplayName("password and cleanup settings agree between Java and application.yaml")
    void passwordAndCleanup() throws IOException {
        assertThat(yamlDefault("mastersmith.auth.password.bcrypt-cost"))
                .isEqualTo(javaDefault(PasswordProperties.class, "bcryptCost"))
                .isEqualTo("12");
        assertThat(yamlDefault("mastersmith.auth.refresh-token-cleanup.retention"))
                .isEqualTo(javaDefault(AuthProperties.RefreshTokenCleanup.class, "retention"));
        assertThat(yamlDefault("mastersmith.auth.refresh-token-cleanup.cron"))
                .isEqualTo(javaDefault(AuthProperties.RefreshTokenCleanup.class, "cron"));
    }

    @Test
    @DisplayName("secret settings have empty defaults in application.yaml")
    void secretsAreEmpty() throws IOException {
        assertThat(yamlDefault("mastersmith.auth.signing-key")).isEmpty();
        assertThat(yamlDefault("mastersmith.auth.initial-admin.email")).isEmpty();
        assertThat(yamlDefault("mastersmith.auth.initial-admin.password")).isEmpty();
    }
}
