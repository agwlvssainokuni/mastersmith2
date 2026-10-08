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
package cherry.mastersmith.group.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.group.store.StoreFailureClassifier.Kind;
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
 * 排他と書き込みの例外の区分（{@code reliability-design.md} 1.2 の T6・T7、4.2 の #9、NFR3.3・NFR1.8、計画の 11節 Q5: A）の単体テスト。
 *
 * <p>例外の文は H2 2.4.240 の文の形をまねた値で、行の値（名前の鍵・ID）を含める。区分の後の結果・例外・ログにその値が出ないことを
 * 確かめる。
 */
class StoreFailureClassifierTest {

    /** 違反の文に入る行の値の役（名前の鍵）。 */
    private static final String ROW_VALUE = "trialalpha";

    private static final String NAME_KEY_MESSAGE =
            "Unique index or primary key violation: \"PUBLIC.UK_GROUPS_NAME_KEY_INDEX_2"
                    + " ON PUBLIC.GROUPS(NAME_KEY NULLS FIRST) VALUES ( /* 1 */ '" + ROW_VALUE
                    + "' )\"; SQL statement: insert";

    private static final String MEMBER_KEY_MESSAGE = "Unique index or primary key violation: \"PUBLIC.PRIMARY_KEY_8"
            + " ON PUBLIC.GROUP_MEMBERS(GROUP_ID, USER_ID) VALUES ( /* key:1 */ 11, 22)\"; SQL statement: insert";

    private static final String GROUP_FK_MESSAGE =
            "Referential integrity constraint violation: \"FK_GROUP_MEMBERS_GROUP:"
                    + " PUBLIC.GROUP_MEMBERS FOREIGN KEY(GROUP_ID) REFERENCES PUBLIC.GROUPS(GROUP_ID) (CAST(11 AS BIGINT))\"";

    private static final String USER_FK_MESSAGE = "Referential integrity constraint violation: \"FK_GROUP_MEMBERS_USER:"
            + " PUBLIC.GROUP_MEMBERS FOREIGN KEY(USER_ID) REFERENCES PUBLIC.USERS(USER_ID) (CAST(22 AS BIGINT))\"";

    private static PersistenceException violation(String message, String sqlState, String constraintName) {
        SQLException sql = new SQLException(message, sqlState, Integer.parseInt(sqlState));
        return new ConstraintViolationException("could not execute statement [" + message + "]", sql, constraintName);
    }

    @Test
    @DisplayName("a lock timeout is classified first, also when the SQLException only carries the H2 error code")
    void lockTimeout() {
        SQLException timeout = new SQLException("Timeout trying to lock table GROUPS; " + ROW_VALUE, "HYT00", 50200);

        assertThat(StoreFailureClassifier.classify(new LockTimeoutException("timeout", timeout)))
                .isEqualTo(Kind.LOCK_TIMEOUT);
        assertThat(StoreFailureClassifier.classify(new PersistenceException("wrapped", timeout)))
                .isEqualTo(Kind.LOCK_TIMEOUT);
        assertThat(StoreFailureClassifier.classify(
                        new PersistenceException("wrapped", new SQLException("deadlock", "40001", 40001))))
                .isEqualTo(Kind.LOCK_TIMEOUT);
    }

    @Test
    @DisplayName("a unique violation is classified by the constraint name: name key and member primary key")
    void uniqueViolations() {
        assertThat(StoreFailureClassifier.classify(violation(NAME_KEY_MESSAGE, "23505", "UK_GROUPS_NAME_KEY_INDEX_2")))
                .isEqualTo(Kind.NAME_TAKEN);
        assertThat(StoreFailureClassifier.classify(violation(NAME_KEY_MESSAGE, "23505", null)))
                .as("制約の名前を Hibernate が取り出せなくても、文の制約の名前で見分ける")
                .isEqualTo(Kind.NAME_TAKEN);
        assertThat(StoreFailureClassifier.classify(violation(MEMBER_KEY_MESSAGE, "23505", "PRIMARY_KEY_8")))
                .isEqualTo(Kind.ALREADY_MEMBER);
        assertThat(StoreFailureClassifier.classify(
                        violation("Unique index or primary key violation", "23505", "PK_GROUP_MEMBERS")))
                .isEqualTo(Kind.ALREADY_MEMBER);
    }

    @Test
    @DisplayName(
            "a foreign key violation to the group is REFERENCED for both the delete (23503) and insert (23506) forms")
    void foreignKeyViolations() {
        assertThat(StoreFailureClassifier.classify(violation(GROUP_FK_MESSAGE, "23503", "FK_GROUP_MEMBERS_GROUP")))
                .isEqualTo(Kind.REFERENCED);
        assertThat(StoreFailureClassifier.classify(violation(GROUP_FK_MESSAGE, "23506", "FK_GROUP_MEMBERS_GROUP")))
                .isEqualTo(Kind.REFERENCED);
    }

    @Test
    @DisplayName("a violation after waiting has the same classification as one without waiting (#9)")
    void violationAfterWaiting() {
        PersistenceException afterWaiting = new PersistenceException(
                "flush failed", violation(NAME_KEY_MESSAGE, "23505", "UK_GROUPS_NAME_KEY_INDEX_2"));

        assertThat(StoreFailureClassifier.classify(afterWaiting)).isEqualTo(Kind.NAME_TAKEN);
    }

    @Test
    @DisplayName("other constraints, the user foreign key and other exceptions are unexpected")
    void unexpected() {
        assertThat(StoreFailureClassifier.classify(violation(USER_FK_MESSAGE, "23506", "FK_GROUP_MEMBERS_USER")))
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(StoreFailureClassifier.classify(violation(
                        "Unique index or primary key violation: \"PUBLIC.UK_USERS_EMAIL ON PUBLIC.USERS(EMAIL)\"",
                        "23505",
                        "UK_USERS_EMAIL")))
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(StoreFailureClassifier.classify(violation(NAME_KEY_MESSAGE, "23514", "UK_GROUPS_NAME_KEY")))
                .as("一意の違反でない SQLState では名前があっても読み替えない")
                .isEqualTo(Kind.UNEXPECTED);
        assertThat(StoreFailureClassifier.classify(new PersistenceException("other")))
                .isEqualTo(Kind.UNEXPECTED);
    }

    @Test
    @DisplayName("the outcomes carry no exception text, and an unexpected failure keeps only the class name")
    void outcomes() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(StoreFailureClassifierTest.class);
        PersistenceException unexpected = violation(USER_FK_MESSAGE, "23506", "FK_GROUP_MEMBERS_USER");

        assertThat(StoreFailureClassifier.<Void>toOutcome(
                        violation(NAME_KEY_MESSAGE, "23505", "UK_GROUPS_NAME_KEY"), "GROUP_NAME_KEY", logger))
                .isEqualTo(new StoreOutcome.NameTaken<Void>());
        assertThat(StoreFailureClassifier.<Void>toOutcome(
                        violation(MEMBER_KEY_MESSAGE, "23505", null), "GROUP_MEMBER_KEY", logger))
                .isEqualTo(new StoreOutcome.AlreadyMember<Void>());
        assertThat(StoreFailureClassifier.<Void>toOutcome(
                        violation(GROUP_FK_MESSAGE, "23503", null), "GROUP_ROW", logger))
                .isEqualTo(new StoreOutcome.Referenced<Void>());
        assertThatThrownBy(() -> StoreFailureClassifier.toOutcome(unexpected, "GROUP_MEMBER_KEY", logger))
                .isInstanceOfSatisfying(GroupStoreUnexpectedException.class, wrapped -> {
                    assertThat(wrapped.getCause()).as("元の連なりを持ち出さない").isNull();
                    assertThat(wrapped.getOriginalClass()).isEqualTo(ConstraintViolationException.class.getName());
                    assertThat(wrapped.getMessage())
                            .contains(ConstraintViolationException.class.getName())
                            .doesNotContain("22")
                            .doesNotContain("23506")
                            .doesNotContain("FK_GROUP_MEMBERS_USER")
                            .doesNotContain("USERS");
                });
    }

    @Test
    @DisplayName("a lock timeout becomes Busy with one WARN that has only the lock kind and the exception class")
    void lockTimeoutWarnsOnce() {
        LockTimeoutException timeout =
                new LockTimeoutException("timeout " + ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200));

        try (LogEvents logs = LogEvents.capture(StoreFailureClassifierTest.class)) {
            StoreOutcome<Void> outcome = StoreFailureClassifier.toOutcome(
                    timeout, GroupStore.GROUP_ROW, LoggerFactory.getLogger(StoreFailureClassifierTest.class));

            assertThat(outcome).isEqualTo(new StoreOutcome.Busy<Void>("GROUP_ROW"));
            assertThat(outcome.isDone()).isFalse();
            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getLevel()).isEqualTo(Level.WARN);
                assertThat(warn.getThrowableProxy()).as("例外そのものはログに渡さない").isNull();
                assertThat(keyValues(warn))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "lockKind", "GROUP_ROW", "exceptionClass", LockTimeoutException.class.getName()));
                assertThat(warn.getFormattedMessage()).doesNotContain(ROW_VALUE);
            });
        }
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs() == null
                ? Map.of()
                : event.getKeyValuePairs().stream()
                        .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }
}
