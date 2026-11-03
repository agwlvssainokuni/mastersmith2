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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.user.domain.DisplayTimeZoneInput;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.PasswordChangeFailureReason;
import cherry.mastersmith.user.domain.PasswordChangeOutcome;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import cherry.mastersmith.user.domain.PasswordHash;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * プリファレンスの読み書きとパスワードの変更の業務処理（BR3.1〜BR3.5、BR4.1〜BR4.5、NFR5.1）の単体テスト。
 *
 * <p>DB アクセスと出来事の知らせは Mockito で差し替える。トランザクションは、実際にトランザクションが動いている印を立てる何もしない
 * トランザクションの管理で囲み、パスワードの照合と新しいハッシュの計算のときにトランザクションが動いていないこと（接続を持たないこと）を
 * 確かめる。
 */
class UserPreferencesServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    private static final String CURRENT = "今のパスワード-000001";

    private static final String NEW = "新しいパスワード-00002";

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.50", "Mozilla/5.0", "trace-0050");

    private static final Preferences STORED = new Preferences("山田 花子", Language.JA, Theme.SYSTEM, FontSize.MD);

    private final UserRepository repository = mock(UserRepository.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final PasswordChangeBarrier barrier = mock(PasswordChangeBarrier.class);

    private final RecordingEncoder encoder = new RecordingEncoder();

    private final List<Boolean> transactionActiveAtPublish = new ArrayList<>();

    private String storedHash;

    private UserPreferencesService service;

    /** 実際にトランザクションが動いている印（actualTransactionActive）だけを立てる、何もしないトランザクションの管理。 */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 何もしない（テストでは接続を使わない）。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // 何もしない。
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // 何もしない。
        }
    }

    /** 照合と新しいハッシュの計算の回数と、そのときトランザクションが動いていたかを記録する（cost 4）。 */
    static class RecordingEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);

        final List<Boolean> transactionActive = new ArrayList<>();

        int matches;

        int encodes;

        @Override
        public String encode(CharSequence rawPassword) {
            encodes++;
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matches++;
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return delegate.matches(rawPassword, encodedPassword);
        }
    }

    @BeforeEach
    void setUp() {
        storedHash = new BCryptPasswordEncoder(4).encode(CURRENT);
        service = new UserPreferencesService(
                repository,
                encoder,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NoOpTransactionManager(),
                barrier);
        doAnswer(invocation -> {
                    transactionActiveAtPublish.add(TransactionSynchronizationManager.isActualTransactionActive());
                    return null;
                })
                .when(publisher)
                .publishEvent(any(Object.class));
    }

    private User user(long id) {
        User user = new User("user@example.com", storedHash, false, NOW, STORED);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private PasswordChangedEvent publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(1)).publishEvent(captor.capture());
        return (PasswordChangedEvent) captor.getValue();
    }

    @Test
    @DisplayName("reading returns the stored four values and UserNotFound when the user row is gone")
    void getPreferences() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.findById(8L)).thenReturn(Optional.empty());

        assertThat(service.getPreferences(7)).isEqualTo(new PreferencesResult.Ok(STORED, null));
        assertThat(service.getPreferences(8)).isEqualTo(new PreferencesResult.UserNotFound());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("saving with an error returns every field error and never touches the database")
    void saveInvalid() {
        PreferencesResult result =
                service.savePreferences(7, command("", "EN", "dark", "md", new DisplayTimeZoneInput.Keep()));

        assertThat(result)
                .isEqualTo(new PreferencesResult.Invalid(List.of(
                        new FieldError("displayName", FieldErrorReason.REQUIRED),
                        new FieldError("language", FieldErrorReason.INVALID_VALUE))));
        verifyNoInteractions(repository, publisher);
    }

    private static PreferencesCommand command(
            String displayName, String language, String theme, String fontSize, DisplayTimeZoneInput zone) {
        return new PreferencesCommand(displayName, language, theme, fontSize, zone);
    }

    /** 保存した後の 2 列（インターフェースの取り出し口の名前をそのまま成分の名前にした値）。 */
    private record Saved(Long getUserId, String getDisplayTimeZone) implements UserRepository.SavedPreferences {}

    private static UserRepository.SavedPreferences saved(String zone) {
        return new Saved(7L, zone);
    }

    @Test
    @DisplayName(
            "saving valid values with no zone item keeps the zone column, returns the stripped values and publishes nothing")
    void saveValid() {
        Preferences expected = new Preferences("山田 太郎", Language.EN, Theme.DARK, FontSize.LG);
        when(repository.savePreferences(7L, expected, false, null)).thenReturn(Optional.of(saved("Asia/Tokyo")));

        PreferencesResult result =
                service.savePreferences(7, command(" 山田 太郎　", "en", "dark", "lg", new DisplayTimeZoneInput.Keep()));

        assertThat(result).isEqualTo(new PreferencesResult.Ok(expected, "Asia/Tokyo"));
        verify(repository, times(1)).savePreferences(7L, expected, false, null);
        verify(repository, never()).findById(anyLong());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Clear writes null and Text writes the name, each with one statement")
    void saveClearAndSet() {
        Preferences expected = new Preferences("山田 太郎", Language.EN, Theme.DARK, FontSize.LG);
        when(repository.savePreferences(7L, expected, true, null)).thenReturn(Optional.of(saved(null)));
        when(repository.savePreferences(7L, expected, true, "America/New_York"))
                .thenReturn(Optional.of(saved("America/New_York")));

        assertThat(service.savePreferences(7, command("山田 太郎", "en", "dark", "lg", new DisplayTimeZoneInput.Clear())))
                .isEqualTo(new PreferencesResult.Ok(expected, null));
        assertThat(service.savePreferences(
                        7, command("山田 太郎", "en", "dark", "lg", new DisplayTimeZoneInput.Text("America/New_York"))))
                .isEqualTo(new PreferencesResult.Ok(expected, "America/New_York"));
    }

    @Test
    @DisplayName("an invalid zone name or a non-text value never issues a statement and lists the zone error last")
    void saveInvalidZone() {
        PreferencesResult bad =
                service.savePreferences(7, command("名前", "xx", "dark", "md", new DisplayTimeZoneInput.Text("+09:00")));
        PreferencesResult notText = service.savePreferences(
                7, command("名前", "ja", "dark", "md", new DisplayTimeZoneInput.NotText("NUMBER")));

        assertThat(bad)
                .isEqualTo(new PreferencesResult.Invalid(List.of(
                        new FieldError("language", FieldErrorReason.INVALID_VALUE),
                        new FieldError("displayTimeZone", FieldErrorReason.INVALID_VALUE))));
        assertThat(notText)
                .isEqualTo(new PreferencesResult.Invalid(
                        List.of(new FieldError("displayTimeZone", FieldErrorReason.INVALID_VALUE))));
        verifyNoInteractions(repository, publisher);
    }

    @Test
    @DisplayName("saving for a user row that is gone returns UserNotFound")
    void saveForMissingUser() {
        when(repository.savePreferences(eq(8L), any(Preferences.class), anyBoolean(), any()))
                .thenReturn(Optional.empty());

        assertThat(service.savePreferences(8, command("名前", "ja", "light", "sm", new DisplayTimeZoneInput.Keep())))
                .isEqualTo(new PreferencesResult.UserNotFound());
    }

    @Test
    @DisplayName("a password input error returns the field errors without reading, matching or auditing")
    void passwordInputError() {
        PasswordChangeResult result =
                service.changePassword(7, PasswordChangeCommand.of(null, "short", "other"), ORIGIN);

        assertThat(result)
                .isEqualTo(new PasswordChangeResult.Invalid(List.of(
                        new FieldError("currentPassword", FieldErrorReason.REQUIRED),
                        new FieldError("newPassword", FieldErrorReason.TOO_SHORT),
                        new FieldError("newPasswordConfirmation", FieldErrorReason.MISMATCH))));
        assertThat(encoder.matches).isZero();
        verifyNoInteractions(repository, publisher, barrier);
    }

    @Test
    @DisplayName("a current password over 72 bytes is never passed to the encoder and is audited as a mismatch")
    void tooLongCurrentPassword() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));

        PasswordChangeResult result =
                service.changePassword(7, PasswordChangeCommand.of("a".repeat(73), NEW, NEW), ORIGIN);

        assertThat(result).isEqualTo(new PasswordChangeResult.CurrentMismatch());
        assertThat(encoder.matches).isZero();
        assertThat(publishedEvent().failureReason()).isEqualTo(PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH);
        verify(repository, never()).updatePasswordHashIfUnchanged(anyLong(), any(), any());
    }

    @Test
    @DisplayName("a wrong current password publishes one FAILURE outside any transaction and never updates")
    void wrongCurrentPassword() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));

        PasswordChangeResult result =
                service.changePassword(7, PasswordChangeCommand.of("まちがったパスワード-9", NEW, NEW), ORIGIN);

        assertThat(result).isEqualTo(new PasswordChangeResult.CurrentMismatch());
        PasswordChangedEvent event = publishedEvent();
        assertThat(event.result()).isEqualTo(PasswordChangeOutcome.FAILURE);
        assertThat(event.userId()).isEqualTo(7);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.sourceIp()).isEqualTo("192.0.2.50");
        assertThat(transactionActiveAtPublish).containsExactly(false);
        assertThat(encoder.encodes).isZero();
        verify(repository, never()).updatePasswordHashIfUnchanged(anyLong(), any(), any());
        verifyNoInteractions(barrier);
    }

    @Test
    @DisplayName(
            "a correct current password writes the new hash conditionally and publishes one SUCCESS in the transaction")
    void changes() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.updatePasswordHashIfUnchanged(eq(7L), any(PasswordHash.class), any(PasswordHash.class)))
                .thenReturn(1);

        PasswordChangeResult result = service.changePassword(7, PasswordChangeCommand.of(CURRENT, NEW, NEW), ORIGIN);

        assertThat(result).isEqualTo(new PasswordChangeResult.Changed());
        ArgumentCaptor<PasswordHash> read = ArgumentCaptor.forClass(PasswordHash.class);
        ArgumentCaptor<PasswordHash> written = ArgumentCaptor.forClass(PasswordHash.class);
        verify(repository).updatePasswordHashIfUnchanged(eq(7L), read.capture(), written.capture());
        assertThat(read.getValue().value()).isEqualTo(storedHash);
        assertThat(new BCryptPasswordEncoder(4).matches(NEW, written.getValue().value()))
                .isTrue();
        assertThat(publishedEvent().result()).isEqualTo(PasswordChangeOutcome.SUCCESS);
        assertThat(transactionActiveAtPublish).containsExactly(true);
        InOrder order = inOrder(barrier, repository);
        order.verify(barrier).beforeWrite(7L);
        order.verify(repository).updatePasswordHashIfUnchanged(eq(7L), any(), any());
    }

    @Test
    @DisplayName("matching and hashing run while no transaction is active, so no connection is held (NFR5.1)")
    void bcryptRunsOutsideTransactions() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.updatePasswordHashIfUnchanged(eq(7L), any(PasswordHash.class), any(PasswordHash.class)))
                .thenReturn(1);

        service.changePassword(7, PasswordChangeCommand.of(CURRENT, NEW, NEW), ORIGIN);

        assertThat(encoder.matches).isEqualTo(1);
        assertThat(encoder.encodes).isEqualTo(1);
        assertThat(encoder.transactionActive).containsExactly(false, false);
    }

    @Test
    @DisplayName("changing to the same password as the current one is accepted")
    void samePassword() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.updatePasswordHashIfUnchanged(eq(7L), any(PasswordHash.class), any(PasswordHash.class)))
                .thenReturn(1);

        assertThat(service.changePassword(7, PasswordChangeCommand.of(CURRENT, CURRENT, CURRENT), ORIGIN))
                .isEqualTo(new PasswordChangeResult.Changed());
    }

    @Test
    @DisplayName(
            "when another change was committed after matching, the request is a mismatch audited after the transaction")
    void changedByOther() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.updatePasswordHashIfUnchanged(eq(7L), any(PasswordHash.class), any(PasswordHash.class)))
                .thenReturn(0);
        when(repository.existsById(7L)).thenReturn(true);

        PasswordChangeResult result = service.changePassword(7, PasswordChangeCommand.of(CURRENT, NEW, NEW), ORIGIN);

        assertThat(result).isEqualTo(new PasswordChangeResult.CurrentMismatch());
        assertThat(publishedEvent().result()).isEqualTo(PasswordChangeOutcome.FAILURE);
        assertThat(transactionActiveAtPublish).containsExactly(false);
    }

    @Test
    @DisplayName("a user row gone before reading or before writing returns UserNotFound without auditing")
    void userGone() {
        when(repository.findById(8L)).thenReturn(Optional.empty());
        assertThat(service.changePassword(8, PasswordChangeCommand.of(CURRENT, NEW, NEW), ORIGIN))
                .isEqualTo(new PasswordChangeResult.UserNotFound());
        assertThat(encoder.matches).isZero();

        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.updatePasswordHashIfUnchanged(eq(7L), any(PasswordHash.class), any(PasswordHash.class)))
                .thenReturn(0);
        when(repository.existsById(7L)).thenReturn(false);
        assertThat(service.changePassword(7, PasswordChangeCommand.of(CURRENT, NEW, NEW), ORIGIN))
                .isEqualTo(new PasswordChangeResult.UserNotFound());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("the string forms of the inputs never carry the passwords or the name")
    void inputsMaskSecrets() {
        assertThat(PasswordChangeCommand.of(CURRENT, NEW, NEW).toString())
                .doesNotContain(CURRENT)
                .doesNotContain(NEW);
        assertThat(new PreferencesCommand(
                                "user@example.com", "ja", "system", "md", new DisplayTimeZoneInput.Text("SECRET-ZONE"))
                        .toString())
                .doesNotContain("user@example.com")
                .doesNotContain("SECRET-ZONE");
    }
}
