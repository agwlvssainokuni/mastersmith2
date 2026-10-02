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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.service.ProfileCommand;
import cherry.mastersmith.user.service.UserAdminSummary;
import cherry.mastersmith.useradmin.service.UserAdminEntry;
import cherry.mastersmith.useradmin.service.UserAdminPage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 利用者の管理の応答と要求の型（契約 C3、BR1.8・BR7.4・BR7.5）の単体テスト。 */
class UserAdminWebTypesTest {

    private static final String EMAIL = "web-types-7f3a@example.com";

    private static final String NAME = "漏れ確認 花子";

    private static final Instant REGISTERED = Instant.parse("2026-09-22T00:00:00Z");

    private static final Instant UNTIL = Instant.parse("2026-09-22T00:30:00Z");

    private static UserAdminEntry entry(LockView lock, boolean self) {
        return new UserAdminEntry(
                new UserAdminSummary(7L, EMAIL, NAME, Language.EN, true, false, REGISTERED), lock, self);
    }

    @Test
    @DisplayName("an admin user row copies the eleven values and hides the email and the name as a string")
    void adminUser() {
        AdminUser user = AdminUser.from(entry(new LockView(true, UNTIL, true), true));

        assertThat(user)
                .isEqualTo(new AdminUser(7L, EMAIL, NAME, "en", true, false, true, UNTIL, true, REGISTERED, true));
        assertThat(user.toString()).doesNotContain(EMAIL).doesNotContain(NAME).contains("userId=7");
    }

    @Test
    @DisplayName("a row that is not locked has no unlock time, and a page copies its values and hides the rows")
    void notLockedAndPage() {
        AdminUserPage page = AdminUserPage.from(new UserAdminPage(List.of(entry(LockView.NONE, false)), 3, 20, 41));

        assertThat(page.items()).singleElement().satisfies(user -> {
            assertThat(user.locked()).isFalse();
            assertThat(user.lockedUntil()).isNull();
            assertThat(user.resettable()).isFalse();
            assertThat(user.self()).isFalse();
        });
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(41);
        assertThat(page.toString()).doesNotContain(EMAIL).doesNotContain(NAME).contains("items=1");
        assertThat(AdminUserPage.from(new UserAdminPage(List.of(), 9, 20, 41)).items())
                .isEmpty();
    }

    @Test
    @DisplayName("a profile request hides the name as a string and becomes the command with the raw values")
    void profileRequest() {
        ProfileRequest request = new ProfileRequest("  " + NAME + " ", "ja");

        assertThat(request.toString()).doesNotContain(NAME).contains("language=ja");
        assertThat(request.toCommand()).isEqualTo(new ProfileCommand("  " + NAME + " ", "ja"));
        assertThat(new ProfileRequest(null, null).toCommand()).isEqualTo(new ProfileCommand(null, null));
    }
}
