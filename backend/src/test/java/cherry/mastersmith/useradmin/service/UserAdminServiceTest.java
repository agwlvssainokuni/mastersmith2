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
package cherry.mastersmith.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.service.LockAdministrationService;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.service.ProfileCommand;
import cherry.mastersmith.user.service.ProfileUpdateResult;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSlice;
import cherry.mastersmith.user.service.UserAdminSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 利用者の管理の業務処理（B3 の一覧と氏名・言語の変更、BR1.1〜BR1.9・BR5.1〜BR5.3）の単体テスト。 */
class UserAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private final UserAccountService userAccounts = mock(UserAccountService.class);

    private final LockAdministrationService locks = mock(LockAdministrationService.class);

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new UserAdminService(userAccounts, locks, transactionManager);
    }

    private static UserAdminSummary summary(long id) {
        return new UserAdminSummary(id, "user" + id + "@example.com", "利用者 " + id, Language.JA, false, false, NOW);
    }

    @Test
    @DisplayName("a page is read in one read-only transaction with lock views and the self flag")
    void listsPage() {
        when(userAccounts.findAdminPage(null, 20, 20))
                .thenReturn(new UserAdminSlice(List.of(summary(5), summary(6)), 22));
        LockView locked = new LockView(true, NOW.plusSeconds(60), true);
        when(locks.lockViewsOf(List.of(5L, 6L))).thenReturn(Map.of(5L, locked, 6L, LockView.NONE));

        UserAdminListResult result = service.list(6, "2", null);

        assertThat(result).isInstanceOf(UserAdminListResult.Listed.class);
        UserAdminPage page = ((UserAdminListResult.Listed) result).page();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(22);
        assertThat(page.items())
                .containsExactly(
                        new UserAdminEntry(summary(5), locked, false),
                        new UserAdminEntry(summary(6), LockView.NONE, true));
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("an invalid page is rejected before reading anything")
    void invalidPage() {
        for (String page : new String[] {"0", "abc", "", "1234567890"}) {
            assertThat(service.list(1, page, null)).isEqualTo(new UserAdminListResult.InvalidPage());
        }
        verifyNoInteractions(userAccounts, locks, transactionManager);
    }

    @Test
    @DisplayName("a search longer than 254 code points is rejected with q and TOO_LONG")
    void searchTooLong() {
        UserAdminListResult result = service.list(1, null, new SearchText("a".repeat(255)));

        assertThat(result)
                .isEqualTo(new UserAdminListResult.Invalid(List.of(new FieldError("q", FieldErrorReason.TOO_LONG))));
        verifyNoInteractions(userAccounts, locks);
    }

    @Test
    @DisplayName("when both page and q are invalid the page error wins")
    void pageErrorWins() {
        assertThat(service.list(1, "0", new SearchText("a".repeat(255))))
                .isEqualTo(new UserAdminListResult.InvalidPage());
    }

    @Test
    @DisplayName("the search text is passed through unchanged, and a missing page means page 1")
    void passesSearch() {
        SearchText q = new SearchText("  ");
        when(userAccounts.findAdminPage(eq(q), anyLong(), anyInt())).thenReturn(new UserAdminSlice(List.of(), 0));
        when(locks.lockViewsOf(List.of())).thenReturn(Map.of());

        UserAdminListResult result = service.list(1, null, q);

        verify(userAccounts).findAdminPage(q, 0, 20);
        assertThat(((UserAdminListResult.Listed) result).page().items()).isEmpty();
        assertThat(((UserAdminListResult.Listed) result).page().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("a user missing from the lock views is shown as not locked")
    void missingLockView() {
        when(userAccounts.findAdminPage(null, 0, 20)).thenReturn(new UserAdminSlice(List.of(summary(7)), 1));
        when(locks.lockViewsOf(List.of(7L))).thenReturn(Map.of());

        UserAdminPage page = ((UserAdminListResult.Listed) service.list(1, "1", null)).page();

        assertThat(page.items().getFirst().lock()).isEqualTo(LockView.NONE);
    }

    @Test
    @DisplayName("the profile change is passed in one read-write transaction and its result is returned as is")
    void updateProfile() {
        ProfileCommand command = new ProfileCommand("新しい 氏名", "en");
        when(userAccounts.updateProfile(7, command)).thenReturn(new ProfileUpdateResult.Updated());
        when(userAccounts.updateProfile(8, command)).thenReturn(new ProfileUpdateResult.NotFound());

        assertThat(service.updateProfile(7, command)).isEqualTo(new ProfileUpdateResult.Updated());
        assertThat(service.updateProfile(8, command)).isEqualTo(new ProfileUpdateResult.NotFound());
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(definition.capture());
        assertThat(definition.getAllValues())
                .allSatisfy(def -> assertThat(def.isReadOnly()).isFalse());
    }

    @Test
    @DisplayName("the business layer has no event publisher, so neither operation can emit audit events")
    void noAuditEvents() {
        assertThat(UserAdminService.class.getDeclaredFields())
                .noneMatch(field -> ApplicationEventPublisher.class.isAssignableFrom(field.getType()));
    }

    @Test
    @DisplayName("entries and pages hide emails and names in their strings")
    void stringsHideValues() {
        UserAdminSummary leak =
                new UserAdminSummary(3, "leak-check@example.com", "漏れ確認 花子", Language.JA, true, false, NOW);
        UserAdminPage page = new UserAdminPage(List.of(new UserAdminEntry(leak, LockView.NONE, true)), 1, 20, 1);

        assertThat(page.toString())
                .doesNotContain("leak-check")
                .doesNotContain("漏れ確認")
                .contains("self=true");
    }
}
