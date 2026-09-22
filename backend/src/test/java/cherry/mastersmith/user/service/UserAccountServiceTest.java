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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final String HASH = "$2a$04$storedhashstoredhashstoredhashstoredhashstoredhashst";

    private final UserRepository repository = mock(UserRepository.class);

    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private DummyPasswordHash dummy;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        dummy = new DummyPasswordHash(new BCryptPasswordEncoder(4), new PasswordProperties(4));
        service = new UserAccountService(repository, encoder, dummy, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User user(long id) {
        User user = new User("admin@example.com", HASH, true, NOW);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    @Test
    @DisplayName("an existing user with the correct password matches after normalizing the email")
    void matches() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(7)));
        when(encoder.matches("正しいパスワード1234", HASH)).thenReturn(true);

        PasswordVerification result = service.verifyPassword("  Admin@Example.com ", new Password("正しいパスワード1234"));

        assertThat(result.matched()).isTrue();
        assertThat(result.email()).isEqualTo("admin@example.com");
        assertThat(result.userSummary()).contains(new UserSummary(7, "admin@example.com", true));
    }

    @Test
    @DisplayName("an existing user with a wrong password does not match")
    void mismatch() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(7)));

        PasswordVerification result = service.verifyPassword("admin@example.com", new Password("まちがい"));

        assertThat(result.matched()).isFalse();
        assertThat(result.userSummary()).isPresent();
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("an unknown email is checked once against the dummy hash and does not match")
    void unknownUser() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

        PasswordVerification result = service.verifyPassword("nobody@example.com", new Password("なにか"));

        assertThat(result.matched()).isFalse();
        assertThat(result.userSummary()).isEmpty();
        verify(encoder, times(1)).matches("なにか", dummy.hash());
        verify(repository, times(1)).findByEmail("nobody@example.com");
    }

    @Test
    @DisplayName("a password over 72 bytes is not passed to the encoder and is checked once against the dummy hash")
    void tooLong() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(7)));
        String tooLong = "あ".repeat(24) + "a";

        PasswordVerification result = service.verifyPassword("admin@example.com", new Password(tooLong));

        assertThat(result.matched()).isFalse();
        verify(encoder, never()).matches(eq(tooLong), anyString());
        verify(encoder, times(1)).matches(dummy.substituteInput(), dummy.hash());
    }

    @Test
    @DisplayName("the verification result and the user summary never carry the password hash")
    void noHashInResults() {
        assertThat(Arrays.stream(PasswordVerification.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .doesNotContain("passwordHash", "hash");
        assertThat(Arrays.stream(UserSummary.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("userId", "email", "admin");
    }

    @Test
    @DisplayName("creating a user stores the hash of the password and publishes UserCreatedEvent")
    void createUser() {
        when(encoder.encode("パスワードは十二文字以上")).thenReturn(HASH);
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "userId", 11L);
            return saved;
        });

        UserSummary summary = service.createUser(" New@Example.com", new Password("パスワードは十二文字以上"), false);

        assertThat(summary).isEqualTo(new UserSummary(11, "new@example.com", false));
        verify(publisher).publishEvent(new UserCreatedEvent(11));
    }

    @Test
    @DisplayName("findById returns the summary and empty for an unknown id")
    void findById() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.findById(8L)).thenReturn(Optional.empty());

        assertThat(service.findById(7)).contains(new UserSummary(7, "admin@example.com", true));
        assertThat(service.findById(8)).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail normalizes the email before the lookup")
    void existsByEmail() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(7)));

        assertThat(service.existsByEmail(" ADMIN@example.com")).isTrue();
        assertThat(service.existsByEmail("other@example.com")).isFalse();
    }
}
