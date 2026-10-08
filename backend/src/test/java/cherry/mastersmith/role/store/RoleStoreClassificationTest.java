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
package cherry.mastersmith.role.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.role.store.RoleStoreClassifier.Kind;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 排他と書き込みの例外の区分（{@code reliability-design.md} 2.3・4.2 の #14、NFR3.3・NFR1.8、計画の 13節 Q5: A・D-5〜D-7）の単体テスト。
 *
 * <p>例外の文は H2 2.4.240 の文の形をまねた値で、行の値（名前の鍵・ID・対象の名前）を含める。区分の後の結果・例外・ログにその値が
 * 出ないことを確かめる。
 */
class RoleStoreClassificationTest {

    /** 違反の文に入る行の値の役（名前の鍵・対象の名前）。 */
    private static final String ROW_VALUE = "leakcheck_role_7f3a";

    private static final String NAME_KEY_MESSAGE = "Unique index or primary key violation: \"PUBLIC.UK_ROLES_NAME_KEY"
            + " INDEX PUBLIC.UK_ROLES_NAME_KEY_INDEX_4 ON PUBLIC.ROLES(NAME_KEY NULLS FIRST) VALUES ( /* 1 */ '"
            + ROW_VALUE + "' )\"; SQL statement: insert";

    private static final String ROLE_FK_MESSAGE = "Referential integrity constraint violation:"
            + " \"FK_PERMISSION_SETTINGS_ROLE: PUBLIC.PERMISSION_SETTINGS FOREIGN KEY(ROLE_ID)"
            + " REFERENCES PUBLIC.ROLES(ROLE_ID) (CAST(4242 AS BIGINT))\"";

    private static final String SETTINGS_PK_MESSAGE = "Unique index or primary key violation: \"PUBLIC.PRIMARY_KEY_9"
            + " ON PUBLIC.PERMISSION_SETTINGS(ROLE_ID, SCHEMA_NAME, TABLE_NAME, COLUMN_NAME) VALUES ( /* key:1 */ 4242, '"
            + ROW_VALUE + "', '', '')\"";

    private static final String CHECK_MESSAGE =
            "Check constraint violation: \"CK_PERMISSION_SETTINGS_MAIN: " + ROW_VALUE + "\"";

    private static final String USER_ASSIGNMENT_PK_MESSAGE = "Unique index or primary key violation:"
            + " \"PUBLIC.PRIMARY_KEY_A ON PUBLIC.USER_ROLE_ASSIGNMENTS(ROLE_ID, USER_ID) VALUES ( /* key:1 */ 4242, 77)\";"
            + " SQL statement: insert into user_role_assignments (assigned_at,role_id,user_id) values (?,?,?)";

    private static final String GROUP_ASSIGNMENT_PK_MESSAGE = "Unique index or primary key violation:"
            + " \"PUBLIC.PRIMARY_KEY_8 ON PUBLIC.GROUP_ROLE_ASSIGNMENTS(ROLE_ID, GROUP_ID) VALUES ( /* key:1 */ 4242, 88)\";"
            + " SQL statement: insert into group_role_assignments (assigned_at,group_id,role_id) values (?,?,?)";

    private static final String SELECTION_PK_MESSAGE = "Unique index or primary key violation:"
            + " \"PUBLIC.PRIMARY_KEY_F ON PUBLIC.WORK_ROLE_SELECTIONS(USER_ID) VALUES ( /* key:1 */ 77)\";"
            + " SQL statement: MERGE INTO work_role_selections (user_id, role_id, updated_at) KEY (user_id) VALUES (?, ?, ?)";

    private static String foreignKeyMessage(String constraint, String table, String column, String parent) {
        return "Referential integrity constraint violation: \"" + constraint + ": PUBLIC." + table + " FOREIGN KEY("
                + column + ") REFERENCES PUBLIC." + parent + "(" + column + ") (CAST(4242 AS BIGINT))\"";
    }

    private static PersistenceException violation(String message, String sqlState, String constraintName) {
        SQLException sql = new SQLException(message, sqlState, Integer.parseInt(sqlState));
        return new ConstraintViolationException("could not execute statement [" + message + "]", sql, constraintName);
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs() == null
                ? Map.of()
                : event.getKeyValuePairs().stream()
                        .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    @Test
    @DisplayName("a lock timeout is classified first, also when the SQLException only carries the H2 error code")
    void lockTimeout() {
        SQLException timeout = new SQLException("Timeout trying to lock table ROLES; " + ROW_VALUE, "HYT00", 50200);

        assertThat(RoleStoreClassifier.classify(new LockTimeoutException("timeout", timeout)))
                .isEqualTo(Kind.LOCK_TIMEOUT);
        assertThat(RoleStoreClassifier.classify(new PersistenceException("wrapped", timeout)))
                .isEqualTo(Kind.LOCK_TIMEOUT);
        assertThat(RoleStoreClassifier.classify(
                        new PersistenceException("wrapped", new SQLException("deadlock", "40001", 40001))))
                .isEqualTo(Kind.LOCK_TIMEOUT);
    }

    @Test
    @DisplayName("a unique violation of the name key is NAME_TAKEN by the constraint name or by the message")
    void nameKey() {
        assertThat(RoleStoreClassifier.classify(violation(NAME_KEY_MESSAGE, "23505", "UK_ROLES_NAME_KEY_INDEX_4")))
                .isEqualTo(Kind.NAME_TAKEN);
        assertThat(RoleStoreClassifier.classify(violation(NAME_KEY_MESSAGE, "23505", null)))
                .isEqualTo(Kind.NAME_TAKEN);
    }

    @Test
    @DisplayName("a foreign key violation to the role is REFERENCED for the delete (23503) and insert (23506) forms")
    void roleForeignKey() {
        assertThat(RoleStoreClassifier.classify(violation(ROLE_FK_MESSAGE, "23503", "FK_PERMISSION_SETTINGS_ROLE")))
                .isEqualTo(Kind.REFERENCED_ROLE);
        assertThat(RoleStoreClassifier.classify(violation(ROLE_FK_MESSAGE, "23506", null)))
                .isEqualTo(Kind.REFERENCED_ROLE);
    }

    @Test
    @DisplayName("a violation after waiting has the same classification as one without waiting (#14)")
    void violationAfterWaiting() {
        PersistenceException afterWaiting =
                new PersistenceException("flush failed", violation(NAME_KEY_MESSAGE, "23505", null));

        assertThat(RoleStoreClassifier.classify(afterWaiting)).isEqualTo(Kind.NAME_TAKEN);
    }

    @Test
    @DisplayName("other constraints, other SQL states and other exceptions are unexpected")
    void unexpected() {
        assertThat(RoleStoreClassifier.classify(violation(SETTINGS_PK_MESSAGE, "23505", "PRIMARY_KEY_9")))
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(RoleStoreClassifier.classify(violation(CHECK_MESSAGE, "23513", "CK_PERMISSION_SETTINGS_MAIN")))
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(RoleStoreClassifier.classify(violation(NAME_KEY_MESSAGE, "23514", "UK_ROLES_NAME_KEY")))
                .as("一意の違反でない SQLState では名前があっても読み替えない")
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(RoleStoreClassifier.classify(violation(
                        "Unique index or primary key violation: \"PUBLIC.UK_GROUPS_NAME_KEY\"", "23505", null)))
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(RoleStoreClassifier.classify(new PersistenceException("other")))
                .isEqualTo(Kind.UNEXPECTED);
    }

    @Test
    @DisplayName(
            "the outcomes carry no exception text, and an unexpected failure keeps only the SQL state and a known name")
    void outcomesAndWrapping() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(RoleStoreClassificationTest.class);

        assertThat(RoleStoreClassifier.<Void>toOutcome(
                        violation(NAME_KEY_MESSAGE, "23505", null), "ROLE_NAME_KEY", logger))
                .isEqualTo(new RoleStoreOutcome.NameTaken<Void>());
        assertThat(RoleStoreClassifier.<Void>toOutcome(violation(ROLE_FK_MESSAGE, "23506", null), "ROLE_ROW", logger))
                .isEqualTo(new RoleStoreOutcome.Referenced<Void>(Referent.ROLE));
        assertThatThrownBy(() -> RoleStoreClassifier.toOutcome(
                        violation(SETTINGS_PK_MESSAGE, "23505", "PRIMARY_KEY_9"), "ROLE_ROW", logger))
                .isInstanceOfSatisfying(RoleStoreUnexpectedException.class, wrapped -> {
                    assertThat(wrapped.getCause()).as("元の連なりを持ち出さない").isNull();
                    assertThat(wrapped.getOriginalClass()).isEqualTo(ConstraintViolationException.class.getName());
                    assertThat(wrapped.getSqlState()).isEqualTo("23505");
                    assertThat(wrapped.getConstraintName()).isEqualTo("PK_PERMISSION_SETTINGS");
                    assertThat(wrapped.getMessage())
                            .contains("23505")
                            .contains("PK_PERMISSION_SETTINGS")
                            .doesNotContain(ROW_VALUE)
                            .doesNotContain("4242");
                });
        assertThatThrownBy(() ->
                        RoleStoreClassifier.toOutcome(violation(CHECK_MESSAGE, "23513", null), "ROLE_ROW", logger))
                .isInstanceOfSatisfying(RoleStoreUnexpectedException.class, wrapped -> {
                    assertThat(wrapped.getConstraintName()).isEqualTo("CK_PERMISSION_SETTINGS_MAIN");
                    assertThat(wrapped.getMessage()).doesNotContain(ROW_VALUE);
                });
        assertThatThrownBy(() -> RoleStoreClassifier.toOutcome(new PersistenceException(ROW_VALUE), "ROLE_ROW", logger))
                .isInstanceOfSatisfying(RoleStoreUnexpectedException.class, wrapped -> {
                    assertThat(wrapped.getSqlState()).isNull();
                    assertThat(wrapped.getConstraintName()).isNull();
                    assertThat(wrapped.getMessage()).doesNotContain(ROW_VALUE);
                });
    }

    @Test
    @DisplayName("a lock timeout becomes Busy with one WARN that has only the lock kind and the exception class")
    void lockTimeoutWarnsOnce() {
        LockTimeoutException timeout =
                new LockTimeoutException("timeout " + ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200));

        try (LogEvents logs = LogEvents.capture(RoleStoreClassificationTest.class)) {
            RoleStoreOutcome<Void> outcome = RoleStoreClassifier.toOutcome(
                    timeout, RoleStore.ROLE_ROW, LoggerFactory.getLogger(RoleStoreClassificationTest.class));

            assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<Void>("ROLE_ROW"));
            assertThat(outcome.isDone()).isFalse();
            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getLevel()).isEqualTo(Level.WARN);
                assertThat(warn.getThrowableProxy()).as("例外そのものはログに渡さない").isNull();
                assertThat(keyValues(warn))
                        .containsExactlyInAnyOrderEntriesOf(
                                Map.of("lockKind", "ROLE_ROW", "exceptionClass", LockTimeoutException.class.getName()));
                assertThat(warn.getFormattedMessage()).doesNotContain(ROW_VALUE);
            });
        }
    }

    @Test
    @DisplayName("the outcomes print only their kind, never the value they carry (R-13)")
    void toStringHasOnlyTheKind() {
        assertThat(new RoleStoreOutcome.Done<>(ROW_VALUE).toString()).isEqualTo("Done");
        assertThat(new RoleStoreOutcome.RoleMissing<>().toString()).isEqualTo("RoleMissing");
        assertThat(new RoleStoreOutcome.NameTaken<>().toString()).isEqualTo("NameTaken");
        assertThat(new RoleStoreOutcome.AlreadyAssigned<>().toString()).isEqualTo("AlreadyAssigned");
        assertThat(new RoleStoreOutcome.Referenced<>(Referent.ROLE).toString()).isEqualTo("Referenced[ROLE]");
        assertThat(new RoleStoreOutcome.Busy<>("ROLE_NAME_KEY").toString()).isEqualTo("Busy[ROLE_NAME_KEY]");
        assertThat(new RoleStoreOutcome.Done<>(null).isDone()).isTrue();
    }

    @Test
    @DisplayName("a primary key violation of the user or group assignment is ALREADY_ASSIGNED (B5)")
    void assignmentPrimaryKeys() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(RoleStoreClassificationTest.class);

        assertThat(RoleStoreClassifier.classify(violation(USER_ASSIGNMENT_PK_MESSAGE, "23505", "PRIMARY_KEY_A")))
                .isEqualTo(Kind.ALREADY_ASSIGNED);
        assertThat(RoleStoreClassifier.classify(violation(GROUP_ASSIGNMENT_PK_MESSAGE, "23505", null)))
                .isEqualTo(Kind.ALREADY_ASSIGNED);
        assertThat(RoleStoreClassifier.<Void>toOutcome(
                        violation(USER_ASSIGNMENT_PK_MESSAGE, "23505", null), RoleStore.ROLE_ASSIGNMENT_KEY, logger))
                .isEqualTo(new RoleStoreOutcome.AlreadyAssigned<Void>());
        assertThat(RoleStoreClassifier.classify(new PersistenceException(
                        "flush failed", violation(GROUP_ASSIGNMENT_PK_MESSAGE, "23505", null))))
                .as("待った後の違反も同じ区分（#14）")
                .isEqualTo(Kind.ALREADY_ASSIGNED);
    }

    @Test
    @DisplayName("a primary key violation of the work role selection is Busy of WORK_ROLE_SELECTION_KEY with one WARN")
    void selectionPrimaryKey() {
        assertThat(RoleStoreClassifier.classify(violation(SELECTION_PK_MESSAGE, "23505", null)))
                .isEqualTo(Kind.SELECTION_KEY);

        try (LogEvents logs = LogEvents.capture(RoleStoreClassificationTest.class)) {
            RoleStoreOutcome<Void> outcome = RoleStoreClassifier.toOutcome(
                    violation(SELECTION_PK_MESSAGE, "23505", null),
                    RoleStore.WORK_ROLE_SELECTION_KEY,
                    LoggerFactory.getLogger(RoleStoreClassificationTest.class));

            assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<Void>("WORK_ROLE_SELECTION_KEY"));
            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getThrowableProxy()).isNull();
                assertThat(keyValues(warn)).containsEntry("lockKind", "WORK_ROLE_SELECTION_KEY");
            });
        }
    }

    @Test
    @DisplayName("the foreign keys of the assignment tables are REFERENCED to the role, the user or the group (B5)")
    void assignmentForeignKeys() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(RoleStoreClassificationTest.class);
        String userRole =
                foreignKeyMessage("FK_USER_ROLE_ASSIGNMENTS_ROLE", "USER_ROLE_ASSIGNMENTS", "ROLE_ID", "ROLES");
        String groupRole =
                foreignKeyMessage("FK_GROUP_ROLE_ASSIGNMENTS_ROLE", "GROUP_ROLE_ASSIGNMENTS", "ROLE_ID", "ROLES");
        String user = foreignKeyMessage("FK_USER_ROLE_ASSIGNMENTS_USER", "USER_ROLE_ASSIGNMENTS", "USER_ID", "USERS");
        String group =
                foreignKeyMessage("FK_GROUP_ROLE_ASSIGNMENTS_GROUP", "GROUP_ROLE_ASSIGNMENTS", "GROUP_ID", "GROUPS");
        String selectionUser =
                foreignKeyMessage("FK_WORK_ROLE_SELECTIONS_USER", "WORK_ROLE_SELECTIONS", "USER_ID", "USERS");

        assertThat(RoleStoreClassifier.classify(violation(userRole, "23503", null)))
                .isEqualTo(Kind.REFERENCED_ROLE);
        assertThat(RoleStoreClassifier.classify(violation(groupRole, "23506", null)))
                .isEqualTo(Kind.REFERENCED_ROLE);
        assertThat(RoleStoreClassifier.classify(violation(user, "23506", null))).isEqualTo(Kind.REFERENCED_USER);
        assertThat(RoleStoreClassifier.classify(violation(selectionUser, "23506", null)))
                .isEqualTo(Kind.REFERENCED_USER);
        assertThat(RoleStoreClassifier.classify(violation(group, "23506", "FK_GROUP_ROLE_ASSIGNMENTS_GROUP")))
                .isEqualTo(Kind.REFERENCED_GROUP);
        assertThat(RoleStoreClassifier.<Void>toOutcome(violation(user, "23506", null), "ROLE_ASSIGNMENT_KEY", logger))
                .isEqualTo(new RoleStoreOutcome.Referenced<Void>(Referent.USER));
        assertThat(RoleStoreClassifier.<Void>toOutcome(violation(group, "23506", null), "ROLE_ASSIGNMENT_KEY", logger))
                .isEqualTo(new RoleStoreOutcome.Referenced<Void>(Referent.GROUP));
        assertThat(RoleStoreClassifier.classify(violation(
                        foreignKeyMessage("FK_GROUP_MEMBERS_GROUP", "GROUP_MEMBERS", "GROUP_ID", "GROUPS"),
                        "23503",
                        null)))
                .as("group の外部キーは role の区分に当たらない")
                .isEqualTo(Kind.UNEXPECTED);
    }

    @Test
    @DisplayName("an unexpected failure on an assignment table keeps only the SQL state and the known constraint name")
    void unexpectedOnAssignmentTables() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(RoleStoreClassificationTest.class);
        String userFk = foreignKeyMessage("FK_USER_ROLE_ASSIGNMENTS_USER", "USER_ROLE_ASSIGNMENTS", "USER_ID", "USERS");

        assertThatThrownBy(() -> RoleStoreClassifier.toOutcome(
                        violation(userFk + " " + ROW_VALUE, "23513", null), "ROLE_ASSIGNMENT_KEY", logger))
                .isInstanceOfSatisfying(RoleStoreUnexpectedException.class, wrapped -> {
                    assertThat(wrapped.getConstraintName()).isEqualTo("FK_USER_ROLE_ASSIGNMENTS_USER");
                    assertThat(wrapped.getMessage()).doesNotContain(ROW_VALUE).doesNotContain("4242");
                });
    }
}
