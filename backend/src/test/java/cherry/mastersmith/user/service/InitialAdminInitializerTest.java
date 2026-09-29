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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Theme;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.KeyValuePair;

class InitialAdminInitializerTest {

    private static final String PASSWORD = "初期管理者のパスワード1";

    private final UserAccountService service = mock(UserAccountService.class);

    private static String render(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + event.getKeyValuePairs();
    }

    /** ログのキー・値をキーの名前で引けるようにする。 */
    private static Map<String, Object> keyValues(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs() == null ? List.of() : event.getKeyValuePairs();
        return pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    /**
     * INFO のログにメールアドレスそのもの（そろえた値・設定の値）が無く、キー {@code email} が無く、キー {@code maskedEmail}
     * に伏せ字だけが載ることを確かめる（Intent 260929-log-deps-cleanup の FR1.1）。
     */
    private static void assertMaskedEmailOnly(ILoggingEvent event, String... rawEmails) {
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        for (String raw : rawEmails) {
            assertThat(render(event)).as("メールアドレスそのものがログに出ていない").doesNotContain(raw);
        }
        assertThat(keyValues(event))
                .as("キー maskedEmail に伏せ字だけが載り、キー email が無い")
                .doesNotContainKey("email")
                .containsEntry("maskedEmail", "a***@example.com");
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
        verify(service, never()).createUser(any(NewUser.class));
    }

    @Test
    @DisplayName("an existing administrator is left untouched, logged at INFO with the masked email only")
    void existing() {
        when(service.existsByEmail("admin@example.com")).thenReturn(true);
        boolean[] created = new boolean[1];

        List<ILoggingEvent> events = run("admin@example.com", PASSWORD, created);

        assertThat(created[0]).isFalse();
        verify(service, never()).createUser(any(NewUser.class));
        assertThat(events).allSatisfy(event -> assertThat(render(event)).doesNotContain(PASSWORD));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("初期管理者は既にいるため、作成しませんでした");
            assertMaskedEmailOnly(event, "admin@example.com");
        });
    }

    @Test
    @DisplayName("a missing administrator is created as admin with the lower-cased email and the initial values,"
            + " logged at INFO with the masked email only")
    void creates() {
        when(service.createUser(any(NewUser.class))).thenReturn(new CreateUserResult.Created(1));
        boolean[] created = new boolean[1];

        List<ILoggingEvent> events = run(" Admin@Example.com ", PASSWORD, created);

        assertThat(created[0]).isTrue();
        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(service).createUser(captor.capture());
        NewUser newUser = captor.getValue();
        assertThat(newUser.email()).isEqualTo("admin@example.com");
        assertThat(newUser.displayName()).as("氏名の初期値はそろえたメールアドレス").isEqualTo("admin@example.com");
        assertThat(newUser.password().value()).isEqualTo(PASSWORD);
        assertThat(newUser.language()).isEqualTo(Language.JA);
        assertThat(newUser.theme()).isEqualTo(Theme.SYSTEM);
        assertThat(newUser.fontSize()).isEqualTo(FontSize.MD);
        assertThat(newUser.admin()).isTrue();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("初期管理者を作成しました");
            assertThat(render(event)).doesNotContain(PASSWORD);
            assertMaskedEmailOnly(event, "admin@example.com", "Admin@Example.com");
        });
    }

    @Test
    @DisplayName(
            "a duplicate created concurrently is treated as already existing, logged at INFO with the masked email only")
    void duplicate() {
        when(service.createUser(any(NewUser.class))).thenReturn(new CreateUserResult.EmailAlreadyUsed());
        boolean[] created = new boolean[1];

        List<ILoggingEvent> events = run("admin@example.com", PASSWORD, created);

        assertThat(created[0]).isFalse();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("初期管理者は既にいるため、作成しませんでした");
            assertThat(render(event)).doesNotContain(PASSWORD);
            assertMaskedEmailOnly(event, "admin@example.com");
        });
    }
}
