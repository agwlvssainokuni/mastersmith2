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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.common.persistence.RowLockAttempt;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.ProfileUpdate;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserAdminRow;
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.repository.UserRowLockRepository;
import java.lang.reflect.RecordComponent;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final String HASH = "$2a$04$storedhashstoredhashstoredhashstoredhashstoredhashst";

    private static final String NEW_PASSWORD = "パスワードは十二文字以上";

    private final UserRepository repository = mock(UserRepository.class);

    private final UserRowLockRepository rowLocks = mock(UserRowLockRepository.class);

    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private DummyPasswordHash dummy;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        dummy = new DummyPasswordHash(new BCryptPasswordEncoder(4), new PasswordProperties(4));
        service = new UserAccountService(
                repository, rowLocks, encoder, dummy, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User user(long id) {
        User user = new User(
                "admin@example.com", HASH, true, NOW, new Preferences("管理者", Language.EN, Theme.DARK, FontSize.LG));
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private User suspendedUser(long id) {
        User user = user(id);
        ReflectionTestUtils.setField(user, "suspended", true);
        return user;
    }

    private static UserSummary adminSummary(long id) {
        return new UserSummary(id, "admin@example.com", true, "管理者", "en", "dark", "lg", false);
    }

    private static NewUser newUser(String email, String displayName) {
        return new NewUser(
                email, displayName, new Password(NEW_PASSWORD), Language.EN, Theme.LIGHT, FontSize.SM, false);
    }

    private void saveAssigns(long id) {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "userId", id);
            return saved;
        });
    }

    @Test
    @DisplayName("an existing user with the correct password matches after normalizing the email")
    void matches() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(7)));
        when(encoder.matches("正しいパスワード1234", HASH)).thenReturn(true);

        PasswordVerification result =
                service.verifyPassword(new RedactedText("  Admin@Example.com "), new Password("正しいパスワード1234"));

        assertThat(result.matched()).isTrue();
        assertThat(result.email()).isEqualTo("admin@example.com");
        assertThat(result.userSummary()).contains(adminSummary(7));
    }

    @Test
    @DisplayName("an existing user with a wrong password does not match")
    void mismatch() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(7)));

        PasswordVerification result =
                service.verifyPassword(new RedactedText("admin@example.com"), new Password("まちがい"));

        assertThat(result.matched()).isFalse();
        assertThat(result.userSummary()).isPresent();
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("an unknown email is checked once against the dummy hash and does not match")
    void unknownUser() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

        PasswordVerification result =
                service.verifyPassword(new RedactedText("nobody@example.com"), new Password("なにか"));

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

        PasswordVerification result =
                service.verifyPassword(new RedactedText("admin@example.com"), new Password(tooLong));

        assertThat(result.matched()).isFalse();
        verify(encoder, never()).matches(eq(tooLong), anyString());
        verify(encoder, times(1)).matches(dummy.substituteInput(), dummy.hash());
    }

    @Test
    @DisplayName("the summary carries the four display values and never the password hash")
    void summaryFields() {
        assertThat(Arrays.stream(PasswordVerification.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .doesNotContain("passwordHash", "hash");
        assertThat(Arrays.stream(UserSummary.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly(
                        "userId", "email", "admin", "displayName", "language", "theme", "fontSize", "suspended");
    }

    @Test
    @DisplayName("the string forms of the summary and the new user never carry the email, the name or the password")
    void stringFormsMaskSecrets() {
        assertThat(adminSummary(7).toString())
                .doesNotContain("admin@example.com")
                .doesNotContain("管理者")
                .contains("theme=dark");
        assertThat(newUser("new@example.com", "新しい 利用者").toString())
                .doesNotContain("new@example.com")
                .doesNotContain("新しい 利用者")
                .doesNotContain(NEW_PASSWORD);
    }

    @Test
    @DisplayName(
            "creating a user stores the hash, the stripped name and the values, and publishes UserCreatedEvent once")
    void createUser() {
        when(encoder.encode(NEW_PASSWORD)).thenReturn(HASH);
        when(repository.existsByRedactedEmail(new RedactedText("new@example.com")))
                .thenReturn(false);
        saveAssigns(11L);

        CreateUserResult result = service.createUser(newUser(" New@Example.com", "　新しい 利用者 "));

        assertThat(result).isEqualTo(new CreateUserResult.Created(11));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(HASH);
        assertThat(saved.getValue().isAdminFlag()).isFalse();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getPreferences())
                .isEqualTo(new Preferences("新しい 利用者", Language.EN, Theme.LIGHT, FontSize.SM));
        verify(publisher, times(1)).publishEvent(new UserCreatedEvent(11));
    }

    @Test
    @DisplayName("an already registered email returns EmailAlreadyUsed without hashing, saving or publishing")
    void createUserWithRegisteredEmail() {
        when(repository.existsByRedactedEmail(new RedactedText("admin@example.com")))
                .thenReturn(true);

        CreateUserResult result = service.createUser(newUser("ADMIN@example.com", "別の人"));

        assertThat(result).isEqualTo(new CreateUserResult.EmailAlreadyUsed());
        verify(encoder, never()).encode(any());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName(
            "a violation of the unique email constraint returns EmailAlreadyUsed and other violations are rethrown")
    void createUserUniqueViolation() {
        when(encoder.encode(NEW_PASSWORD)).thenReturn(HASH);
        when(repository.existsByRedactedEmail(any())).thenReturn(false);
        when(repository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "dup",
                        new ConstraintViolationException(
                                "dup", new SQLException("dup"), "PUBLIC.UK_USERS_EMAIL_INDEX_4")))
                .thenThrow(new DataIntegrityViolationException(
                        "other", new ConstraintViolationException("other", new SQLException("x"), "CK_OTHER")))
                .thenThrow(new DataIntegrityViolationException("no cause"));

        assertThat(service.createUser(newUser("dup@example.com", "重なる人")))
                .isEqualTo(new CreateUserResult.EmailAlreadyUsed());
        assertThatThrownBy(() -> service.createUser(newUser("other@example.com", "ほかの人")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> service.createUser(newUser("plain@example.com", "ほかの人")))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("values that break the rules are refused as unexpected errors without creating or publishing")
    void createUserRefusesInvalidValues() {
        Password password = new Password(NEW_PASSWORD);
        NewUser[] invalid = {
            new NewUser("a@example.com", " ", password, Language.JA, Theme.SYSTEM, FontSize.MD, false),
            new NewUser("a@example.com", "a".repeat(255), password, Language.JA, Theme.SYSTEM, FontSize.MD, false),
            new NewUser("a@example.com", "名​前", password, Language.JA, Theme.SYSTEM, FontSize.MD, false),
            new NewUser("a@example.com", "名前", new Password("short"), Language.JA, Theme.SYSTEM, FontSize.MD, false),
            new NewUser("a@example.com", "名前", null, Language.JA, Theme.SYSTEM, FontSize.MD, false),
            new NewUser("a@example.com", "名前", password, null, Theme.SYSTEM, FontSize.MD, false),
            new NewUser("a@example.com", "名前", password, Language.JA, null, FontSize.MD, false),
            new NewUser("a@example.com", "名前", password, Language.JA, Theme.SYSTEM, null, false),
            new NewUser("not-an-email", "名前", password, Language.JA, Theme.SYSTEM, FontSize.MD, false)
        };

        for (NewUser newUser : invalid) {
            assertThatThrownBy(() -> service.createUser(newUser))
                    .as(newUser.toString())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("a@example.com")
                    .hasMessageNotContaining(NEW_PASSWORD);
        }
        assertThatThrownBy(() -> service.createUser(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository, publisher);
    }

    @Test
    @DisplayName("findById returns the summary with the four values and empty for an unknown id")
    void findById() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.findById(8L)).thenReturn(Optional.empty());

        assertThat(service.findById(7)).contains(adminSummary(7));
        assertThat(service.findById(8)).isEmpty();
    }

    @Test
    @DisplayName("findDisplayName and findLanguage return the stored values and empty for an unknown id")
    void findDisplayNameAndLanguage() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.findById(8L)).thenReturn(Optional.empty());

        assertThat(service.findDisplayName(7)).contains(new RedactedText("管理者"));
        assertThat(service.findDisplayName(7).toString()).doesNotContain("管理者");
        assertThat(service.findLanguage(7)).contains(Language.EN);
        assertThat(service.findDisplayName(8)).isEmpty();
        assertThat(service.findLanguage(8)).isEmpty();
    }

    @Test
    @DisplayName("verifyPassword rejects a null email before any lookup or password check")
    void verifyPasswordRejectsNullEmail() {
        assertThatThrownBy(() -> service.verifyPassword(null, new Password("なにか")))
                .isInstanceOf(NullPointerException.class);
        verify(repository, never()).findByEmail(anyString());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName(
            "the email lookups of the public operations take the email only as a redacted value, never as a String")
    void emailLookupsTakeNoString() {
        for (String name : new String[] {"verifyPassword", "existsByEmail"}) {
            assertThat(Arrays.stream(UserAccountService.class.getMethods())
                            .filter(method -> method.getName().equals(name))
                            .toList())
                    .as("public %s", name)
                    .isNotEmpty()
                    .allSatisfy(
                            method -> assertThat(method.getParameterTypes()[0]).isEqualTo(RedactedText.class));
        }
    }

    @Test
    @DisplayName("existsByEmail with a redacted value normalizes the email and asks the redacted lookup")
    void existsByRedactedEmail() {
        when(repository.existsByRedactedEmail(new RedactedText("admin@example.com")))
                .thenReturn(true);

        assertThat(service.existsByEmail(new RedactedText(" ADMIN@example.com")))
                .isTrue();
        assertThat(service.existsByEmail(new RedactedText("other@example.com"))).isFalse();
        assertThatThrownBy(() -> service.existsByEmail((RedactedText) null)).isInstanceOf(NullPointerException.class);
        verify(repository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("the summaries of findById and verifyPassword carry the suspension as true and false")
    void summaryCarriesSuspension() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.findById(9L)).thenReturn(Optional.of(suspendedUser(9)));
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(suspendedUser(9)));
        when(encoder.matches("正しいパスワード1234", HASH)).thenReturn(true);

        assertThat(service.findById(7))
                .hasValueSatisfying(summary -> assertThat(summary.suspended()).isFalse());
        assertThat(service.findById(9))
                .hasValueSatisfying(summary -> assertThat(summary.suspended()).isTrue());
        PasswordVerification verification =
                service.verifyPassword(new RedactedText("admin@example.com"), new Password("正しいパスワード1234"));
        // 停止中でも照合は今までどおり行い、停止の判定は呼び出し元（ログインの照合）が要約の値で行う。
        assertThat(verification.matched()).isTrue();
        assertThat(verification.user().suspended()).isTrue();
        verify(repository, times(1)).findByEmail("admin@example.com");
    }

    @Test
    @DisplayName("isSuspended returns the stored value and fails for an unknown user with only the id in the message")
    void isSuspended() {
        when(repository.findById(7L)).thenReturn(Optional.of(user(7)));
        when(repository.findById(9L)).thenReturn(Optional.of(suspendedUser(9)));
        when(repository.findById(8L)).thenReturn(Optional.empty());

        assertThat(service.isSuspended(7)).isFalse();
        assertThat(service.isSuspended(9)).isTrue();
        assertThatThrownBy(() -> service.isSuspended(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId=8")
                .hasMessageNotContaining("admin@example.com");
    }

    @Test
    @DisplayName("setSuspended writes only through the suspension update and fails when no row is updated")
    void setSuspended() {
        when(repository.updateSuspended(7L, true)).thenReturn(1);
        when(repository.updateSuspended(7L, false)).thenReturn(1);
        when(repository.updateSuspended(8L, true)).thenReturn(0);

        service.setSuspended(7, true);
        service.setSuspended(7, false);

        verify(repository).updateSuspended(7L, true);
        verify(repository).updateSuspended(7L, false);
        assertThatThrownBy(() -> service.setSuspended(8, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId=8");
        verify(repository, never()).save(any(User.class));
        verify(repository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(publisher, encoder);
    }

    @Test
    @DisplayName("the string form of the summary shows the suspension but not the email or the name")
    void summaryStringShowsSuspension() {
        UserSummary suspended = new UserSummary(9, "suspended@example.com", false, "停止 中", "ja", "system", "md", true);

        assertThat(suspended.toString())
                .contains("suspended=true")
                .contains("userId=9")
                .doesNotContain("suspended@example.com")
                .doesNotContain("停止 中");
        assertThat(adminSummary(7).toString()).contains("suspended=false");
    }

    @Test
    @DisplayName("the string form of a password verification hides the email and the name but shows the result")
    void passwordVerificationStringHidesEmail() {
        PasswordVerification found = new PasswordVerification("admin@example.com", adminSummary(7), true);
        PasswordVerification unknown = new PasswordVerification("nobody@example.com", null, false);

        assertThat(found.toString())
                .doesNotContain("admin@example.com")
                .doesNotContain("管理者")
                .contains("email=***")
                .contains("matched=true")
                .contains("userId=7");
        assertThat(unknown.toString())
                .doesNotContain("nobody@example.com")
                .contains("email=***")
                .contains("user=null")
                .contains("matched=false");
    }

    private static UserAdminRow adminRow(long id) {
        return new UserAdminRow(id, "row@example.com", "一覧 太郎", Language.JA, false, false, NOW);
    }

    @Test
    @DisplayName("without a search the admin page counts all users and reads the unfiltered rows (U3)")
    void adminPageWithoutSearch() {
        when(repository.count()).thenReturn(21L);
        when(repository.findAdminRows(any(Pageable.class))).thenReturn(List.of(adminRow(3)));

        UserAdminSlice slice = service.findAdminPage(null, 20, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAdminRows(pageable.capture());
        assertThat(pageable.getValue().getOffset()).isEqualTo(20);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(slice.total()).isEqualTo(21);
        assertThat(slice.items())
                .containsExactly(new UserAdminSummary(3, "row@example.com", "一覧 太郎", Language.JA, false, false, NOW));
        verify(repository, never()).countBySearch(any());
    }

    @Test
    @DisplayName("a blank search is treated as no search (U3)")
    void adminPageBlankSearch() {
        when(repository.count()).thenReturn(1L);
        when(repository.findAdminRows(any(Pageable.class))).thenReturn(List.of(adminRow(1)));

        service.findAdminPage(new SearchText(" \u3000"), 0, 20);

        verify(repository, never()).countBySearch(any());
        verify(repository, never()).findAdminRowsBySearch(any(), any());
    }

    @Test
    @DisplayName("a search passes the redacted pattern to both the count and the rows (U3)")
    void adminPageWithSearch() {
        when(repository.countBySearch(any())).thenReturn(1L);
        when(repository.findAdminRowsBySearch(any(), any(Pageable.class))).thenReturn(List.of(adminRow(5)));

        UserAdminSlice slice = service.findAdminPage(new SearchText(" Taro_1 "), 0, 20);

        ArgumentCaptor<RedactedText> pattern = ArgumentCaptor.forClass(RedactedText.class);
        verify(repository).countBySearch(pattern.capture());
        verify(repository).findAdminRowsBySearch(eq(pattern.getValue()), any(Pageable.class));
        assertThat(pattern.getValue().value()).isEqualTo("%taro\\_1%");
        assertThat(slice.items()).hasSize(1);
        verify(repository, never()).count();
    }

    @Test
    @DisplayName("an offset at or beyond the total reads no rows (U3)")
    void adminPageBeyondTotal() {
        when(repository.count()).thenReturn(20L);

        UserAdminSlice slice = service.findAdminPage(null, 20, 20);

        assertThat(slice.items()).isEmpty();
        assertThat(slice.total()).isEqualTo(20);
        verify(repository, never()).findAdminRows(any());
    }

    @Test
    @DisplayName("an offset that is not a multiple of the page size is rejected (U3)")
    void adminPageRejectsOddOffset() {
        assertThatThrownBy(() -> service.findAdminPage(null, 5, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAdminPage(null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("an invalid profile is returned without touching the database (U3)")
    void updateProfileInvalid() {
        ProfileUpdateResult result = service.updateProfile(7, new ProfileCommand(" ", "fr"));

        assertThat(result)
                .isEqualTo(new ProfileUpdateResult.Invalid(List.of(
                        new FieldError("displayName", FieldErrorReason.REQUIRED),
                        new FieldError("language", FieldErrorReason.INVALID_VALUE))));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a valid profile writes the stripped name and the language, and zero rows means not found (U3)")
    void updateProfileWritesStrippedValue() {
        when(repository.updateProfile(eq(7L), any())).thenReturn(1);
        when(repository.updateProfile(eq(8L), any())).thenReturn(0);

        ProfileUpdateResult updated = service.updateProfile(7, new ProfileCommand(" 新しい 氏名\u3000", "en"));
        ProfileUpdateResult missing = service.updateProfile(8, new ProfileCommand("だれか", "ja"));

        verify(repository).updateProfile(7L, new ProfileUpdate("新しい 氏名", Language.EN));
        assertThat(updated).isEqualTo(new ProfileUpdateResult.Updated());
        assertThat(missing).isEqualTo(new ProfileUpdateResult.NotFound());
    }

    @Test
    @DisplayName("the admin summary, the slice and the profile command hide emails and names in their strings (U3)")
    void adminTypesHideValues() {
        UserAdminSummary summary =
                new UserAdminSummary(3, "leak-check@example.com", "漏れ確認 花子", Language.JA, true, false, NOW);

        assertThat(summary.toString())
                .doesNotContain("leak-check")
                .doesNotContain("漏れ確認")
                .contains("userId=3");
        assertThat(new UserAdminSlice(List.of(summary), 1).toString())
                .doesNotContain("leak-check")
                .doesNotContain("漏れ確認");
        assertThat(new ProfileCommand("漏れ確認 花子", "ja").toString())
                .doesNotContain("漏れ確認")
                .contains("language=ja");
        assertThat(adminRow(4).toString()).doesNotContain("row@example.com").doesNotContain("一覧 太郎");
    }

    // --- B4: 排他の口・印の書き換え（Intent 260930-user-admin の U3、契約 C8、BR3.1・BR3.3・BR4.1・BR4.2）

    private static UserAdminRow adminRow(long id, boolean admin, boolean suspended) {
        return new UserAdminRow(id, "row" + id + "@example.com", "行 " + id, Language.JA, admin, suspended, NOW);
    }

    @Test
    @DisplayName("a busy admin-rows lock is passed on as Busy and nothing else is read")
    void lockAdminRowsBusy() {
        when(rowLocks.lockAdminRowsAndTarget(9L)).thenReturn(RowLockAttempt.busy());

        assertThat(service.lockAdminRowsInIdOrder(9L)).isEqualTo(new AdminRowsLock.Busy());
        verify(repository, never()).findAdminRow(anyLong());
        verify(repository, never()).findActiveAdminIds();
    }

    @Test
    @DisplayName("after the admin rows are locked the target summary and the active admins are read by other queries")
    void lockAdminRowsAcquired() {
        when(rowLocks.lockAdminRowsAndTarget(9L)).thenReturn(RowLockAttempt.acquired(List.of(1L, 9L)));
        when(repository.findAdminRow(9L)).thenReturn(Optional.of(adminRow(9L, true, false)));
        when(repository.findActiveAdminIds()).thenReturn(List.of(1L, 9L));

        AdminRowsLock lock = service.lockAdminRowsInIdOrder(9L);

        assertThat(lock).isInstanceOfSatisfying(AdminRowsLock.Locked.class, locked -> {
            assertThat(locked.target())
                    .get()
                    .extracting(UserAdminSummary::userId)
                    .isEqualTo(9L);
            assertThat(locked.activeAdminIds()).containsExactlyInAnyOrder(1L, 9L);
        });
        InOrder order = inOrder(rowLocks, repository);
        order.verify(rowLocks).lockAdminRowsAndTarget(9L);
        order.verify(repository).findAdminRow(9L);
        order.verify(repository).findActiveAdminIds();
    }

    @Test
    @DisplayName("an admin-rows lock for a missing target holds no target summary")
    void lockAdminRowsMissingTarget() {
        when(rowLocks.lockAdminRowsAndTarget(9L)).thenReturn(RowLockAttempt.acquired(List.of(1L)));
        when(repository.findAdminRow(9L)).thenReturn(Optional.empty());
        when(repository.findActiveAdminIds()).thenReturn(List.of(1L));

        assertThat(service.lockAdminRowsInIdOrder(9L))
                .isEqualTo(new AdminRowsLock.Locked(Optional.empty(), Set.of(1L)));
    }

    @Test
    @DisplayName("the user row lock reads the summary only when the user exists and passes Busy on")
    void lockUserRow() {
        when(rowLocks.lockUserRow(9L))
                .thenReturn(RowLockAttempt.acquired(true), RowLockAttempt.acquired(false), RowLockAttempt.busy());
        when(repository.findAdminRow(9L)).thenReturn(Optional.of(adminRow(9L, false, true)));

        assertThat(service.lockUserRow(9L))
                .isInstanceOfSatisfying(
                        UserRowLock.Locked.class,
                        locked -> assertThat(locked.target())
                                .get()
                                .extracting(UserAdminSummary::suspended)
                                .isEqualTo(true));
        assertThat(service.lockUserRow(9L)).isEqualTo(new UserRowLock.Locked(Optional.empty()));
        assertThat(service.lockUserRow(9L)).isEqualTo(new UserRowLock.Busy());
        verify(repository, times(1)).findAdminRow(9L);
    }

    @Test
    @DisplayName("the summary without a lock comes from the projection and is empty for an unknown user")
    void findAdminSummary() {
        when(repository.findAdminRow(9L)).thenReturn(Optional.of(adminRow(9L, true, false)));
        when(repository.findAdminRow(10L)).thenReturn(Optional.empty());

        assertThat(service.findAdminSummary(9L)).get().satisfies(summary -> {
            assertThat(summary.admin()).isTrue();
            assertThat(summary.toString()).doesNotContain("row9@example.com");
        });
        assertThat(service.findAdminSummary(10L)).isEmpty();
        verifyNoInteractions(rowLocks);
    }

    @Test
    @DisplayName("setAdmin writes only the flag and fails loudly when no row was updated")
    void setAdmin() {
        when(repository.updateAdminFlag(9L, true)).thenReturn(1);
        when(repository.updateAdminFlag(10L, false)).thenReturn(0);

        service.setAdmin(9L, true);

        verify(repository).updateAdminFlag(9L, true);
        assertThatThrownBy(() -> service.setAdmin(10L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("利用者がいません: userId=10");
    }
}
