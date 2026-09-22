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
package cherry.mastersmith.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.user.domain.Password;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

class InitialAdminInitializerTest {

    private static final String PASSWORD = "初期管理者のパスワード1";

    private final UserAccountService service = mock(UserAccountService.class);

    private static String render(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + event.getKeyValuePairs();
    }

    private List<ILoggingEvent> run(String email, String password, boolean[] created) {
        try (LogEvents events = LogEvents.capture(InitialAdminInitializer.class)) {
            created[0] =
                    new InitialAdminInitializer(new InitialAdminProperties(email, password), service).createIfNeeded();
            return events.list();
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @DisplayName("missing or invalid settings create nothing and warn with the reason and the fix")
    @CsvSource(
            delimiter = '|',
            nullValues = "NULL",
            value = {
                "NULL|初期管理者のパスワード1|メールアドレス（email）が設定されていません",
                "admin@example.com|NULL|パスワード（password）が設定されていません",
                "admin-example.com|初期管理者のパスワード1|メールアドレス（email）の形式が正しくありません",
                "admin@example.com|elevenchars|12 文字未満です",
                "admin@example.com|ああああああああああああああああああああああああa|72 バイトを超えています"
            })
    void invalidSettings(String email, String password, String reason) {
        boolean[] created = new boolean[1];

        List<ILoggingEvent> events = run(email, password, created);

        assertThat(created[0]).isFalse();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(render(event)).contains(reason).contains("再起動すると");
            if (password != null) {
                assertThat(render(event)).doesNotContain(password);
            }
        });
        verify(service, never()).createUser(anyString(), any(Password.class), anyBoolean());
    }

    @Test
    @DisplayName("an existing administrator is left untouched")
    void existing() {
        when(service.existsByEmail("admin@example.com")).thenReturn(true);
        boolean[] created = new boolean[1];

        List<ILoggingEvent> events = run("admin@example.com", PASSWORD, created);

        assertThat(created[0]).isFalse();
        verify(service, never()).createUser(anyString(), any(Password.class), anyBoolean());
        assertThat(events).allSatisfy(event -> assertThat(render(event)).doesNotContain(PASSWORD));
    }

    @Test
    @DisplayName("a missing administrator is created as admin with the lower-cased email and logged at INFO")
    void creates() {
        boolean[] created = new boolean[1];

        List<ILoggingEvent> events = run(" Admin@Example.com ", PASSWORD, created);

        assertThat(created[0]).isTrue();
        verify(service).createUser(eq("admin@example.com"), any(Password.class), eq(true));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(render(event)).contains("admin@example.com").doesNotContain(PASSWORD);
        });
    }

    @Test
    @DisplayName("a duplicate created concurrently is treated as already existing")
    void duplicate() {
        when(service.createUser(anyString(), any(Password.class), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        boolean[] created = new boolean[1];

        run("admin@example.com", PASSWORD, created);

        assertThat(created[0]).isFalse();
    }
}
