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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

/**
 * 違反の区分（BR2.1）。一意の重なり・親の無い子の追加・参照のある行の削除の応答が、H2 の参照のテスト（{@code InvitationAdminApiIT}・
 * {@code RoleAdminApiIT}・{@code RoleAssignmentApiIT}・{@code GroupAdminApiIT}）と同じ状態コードと {@code code} になり、想定外（500）が出ない。
 * メールアドレスの重なりは招待の API で確かめるため、実際に受けるだけの SMTP の受け手を使う。
 */
@InternalDbPostgresIT
class PgViolationClassificationIT {

    /** PostgreSQL の外部キーの違反（NO ACTION。ADR-002・ADR-004）。 */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private static SmtpTestServer receiver;

    private static PgApp app;

    private static PortableFixture fixture;

    @BeforeAll
    static void start() {
        receiver = SmtpTestServer.builder().start();
        app = PgApp.startWithMail(receiver.port());
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        try {
            app.close();
        } finally {
            receiver.close();
        }
    }

    private static void assertNoServerError(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(PortableFixture.describe(response)).isLessThan(500);
    }

    private static String sqlStateOf(String sql, long id) {
        try (Connection connection = app.database().connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
            return null;
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }

    @Test
    @DisplayName(
            "a registered email and a pending email are 409 INVITATION_EMAIL_REGISTERED and INVITATION_ALREADY_PENDING")
    void duplicateEmails(CapturedOutput output) {
        int logStart = output.getAll().length();
        PortableFixture.Account registered = fixture.createUser(false);
        String pending = "pg-pending-" + System.nanoTime() + "@example.com";

        HttpResponse<String> taken = fixture.adminCall(
                "POST",
                "/api/admin/invitations",
                PortableFixture.json(
                        Map.of("email", registered.email().toUpperCase(java.util.Locale.ROOT), "language", "ja")));
        HttpResponse<String> first = fixture.adminCall(
                "POST", "/api/admin/invitations", PortableFixture.json(Map.of("email", pending, "language", "ja")));
        HttpResponse<String> second = fixture.adminCall(
                "POST", "/api/admin/invitations", PortableFixture.json(Map.of("email", pending, "language", "en")));

        PortableFixture.assertProblem(taken, 409, "INVITATION_EMAIL_REGISTERED");
        assertThat(HttpTestClient.json(taken)).doesNotContainKey("invitationId");
        assertThat(first.statusCode()).isEqualTo(201);
        PortableFixture.assertProblem(second, 409, "INVITATION_ALREADY_PENDING");
        assertThat(HttpTestClient.json(second)).containsKeys("invitationId", "page");
        assertThat(output.getAll().substring(logStart))
                .as("the email addresses are not in the application log")
                .doesNotContain(registered.email())
                .doesNotContain(pending);
    }

    @Test
    @DisplayName(
            "a duplicate role name and a duplicate group name are 409 ROLE_NAME_DUPLICATE and GROUP_NAME_DUPLICATE")
    void duplicateNames(CapturedOutput output) {
        int logStart = output.getAll().length();
        String roleName = PortableFixture.uniqueName("role");
        String groupName = PortableFixture.uniqueName("group");
        fixture.createRole(roleName);
        fixture.createGroup(groupName);

        PortableFixture.assertProblem(
                fixture.adminCall("POST", "/api/admin/roles", PortableFixture.json(Map.of("name", roleName))),
                409,
                "ROLE_NAME_DUPLICATE");
        PortableFixture.assertProblem(
                fixture.adminCall("POST", "/api/admin/groups", PortableFixture.json(Map.of("name", groupName))),
                409,
                "GROUP_NAME_DUPLICATE");
        assertThat(output.getAll().substring(logStart))
                .as("the created names are not in the application log")
                .doesNotContain(roleName)
                .doesNotContain(groupName);
    }

    @Test
    @DisplayName("adding a child to a parent that does not exist is 404 with the same code as on H2, never 500")
    void childWithoutParent() {
        PortableFixture.Account member = fixture.createUser(false);
        long roleId = fixture.createRole(PortableFixture.uniqueName("role"));
        long groupId = fixture.createGroup(PortableFixture.uniqueName("group"));
        String assignments = "/api/admin/roles/" + roleId + "/assignments";

        HttpResponse<String> missingUser =
                fixture.adminCall("POST", assignments, PortableFixture.json(Map.of("userId", Long.MAX_VALUE)));
        HttpResponse<String> missingGroup =
                fixture.adminCall("POST", assignments, PortableFixture.json(Map.of("groupId", Long.MAX_VALUE)));
        HttpResponse<String> missingRole = fixture.adminCall(
                "POST",
                "/api/admin/roles/" + Long.MAX_VALUE + "/assignments",
                PortableFixture.json(Map.of("userId", member.userId())));
        HttpResponse<String> memberOfMissingUser = fixture.adminCall(
                "POST",
                "/api/admin/groups/" + groupId + "/members",
                PortableFixture.json(Map.of("userId", Long.MAX_VALUE)));
        HttpResponse<String> memberOfMissingGroup = fixture.adminCall(
                "POST",
                "/api/admin/groups/" + Long.MAX_VALUE + "/members",
                PortableFixture.json(Map.of("userId", member.userId())));

        PortableFixture.assertProblem(missingUser, 404, "USER_NOT_FOUND");
        PortableFixture.assertProblem(missingGroup, 404, "GROUP_NOT_FOUND");
        PortableFixture.assertProblem(missingRole, 404, "ROLE_NOT_FOUND");
        PortableFixture.assertProblem(memberOfMissingUser, 404, "USER_NOT_FOUND");
        PortableFixture.assertProblem(memberOfMissingGroup, 404, "GROUP_NOT_FOUND");
        for (HttpResponse<String> response :
                java.util.List.of(missingUser, missingGroup, missingRole, memberOfMissingUser, memberOfMissingGroup)) {
            assertNoServerError(response);
        }
    }

    @Test
    @DisplayName(
            "deleting a role or a group that is referenced is 409, and a referenced row is refused by the database with 23503 (not 23001)")
    void referencedRowsAreNotDeleted() {
        PortableFixture.Account member = fixture.createUser(false);
        long roleId = fixture.createRole(PortableFixture.uniqueName("role"));
        long groupId = fixture.createGroup(PortableFixture.uniqueName("group"));
        fixture.assignRoleToUser(roleId, member.userId());
        long assignedGroup = fixture.createGroup(PortableFixture.uniqueName("group"));
        long otherRole = fixture.createRole(PortableFixture.uniqueName("role"));
        fixture.assignRoleToGroup(otherRole, assignedGroup);
        fixture.addMember(groupId, member.userId());

        PortableFixture.assertProblem(
                fixture.adminCall("DELETE", "/api/admin/roles/" + roleId, null), 409, "ROLE_IN_USE");
        PortableFixture.assertProblem(
                fixture.adminCall("DELETE", "/api/admin/groups/" + assignedGroup, null), 409, "GROUP_IN_USE");

        assertThat(sqlStateOf("DELETE FROM users WHERE user_id = ?", member.userId()))
                .isEqualTo(FOREIGN_KEY_VIOLATION);
        assertThat(sqlStateOf("DELETE FROM roles WHERE role_id = ?", roleId)).isEqualTo(FOREIGN_KEY_VIOLATION);
        assertThat(sqlStateOf("DELETE FROM groups WHERE group_id = ?", groupId)).isEqualTo(FOREIGN_KEY_VIOLATION);
    }
}
