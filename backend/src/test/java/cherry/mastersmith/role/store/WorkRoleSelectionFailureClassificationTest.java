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

import cherry.mastersmith.common.persistence.KeyedUpsert;
import cherry.mastersmith.common.persistence.KeyedUpsertLostException;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 作業ロールの保存の失敗が {@link RoleStoreClassifier} の区分に届く道の確かめ（Intent 261114-internal-db-postgresql の U2。計画の Step 8）。
 * 待ちの上限切れ（H2・PostgreSQL の形）と、想定外の経路の {@link KeyedUpsertLostException} が、保存の鍵の種類
 * （{@value RoleStore#WORK_ROLE_SELECTION_KEY}）のまま結果の型か想定外の例外になる。
 */
class WorkRoleSelectionFailureClassificationTest {

    /** 例外の文に入りうる行の値の役。 */
    private static final String ROW_VALUE = "leakcheck_selection_5b1c";

    private static RoleStoreOutcome<Void> classify(Throwable failure) {
        return RoleStoreClassifier.toOutcome(
                failure,
                RoleStore.WORK_ROLE_SELECTION_KEY,
                LoggerFactory.getLogger(WorkRoleSelectionFailureClassificationTest.class));
    }

    @Test
    @DisplayName("a lock wait timeout of the H2 shape reaches the work role selection key as Busy")
    void h2TimeoutIsBusy() {
        PersistenceException failure =
                new PersistenceException(new SQLException("Timeout trying to lock", "HYT00", 50200));

        assertThat(classify(failure)).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));
    }

    @Test
    @DisplayName("a lock_not_available failure of the PostgreSQL shape reaches the work role selection key as Busy")
    void postgresLockNotAvailableIsBusy() {
        PersistenceException failure =
                new PersistenceException(new SQLException("could not obtain lock on row", "55P03", 0));

        assertThat(classify(failure)).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));
    }

    @Test
    @DisplayName("a primary key violation of the PostgreSQL shape on the selection table is Busy as well")
    void postgresPrimaryKeyViolationIsBusy() {
        PersistenceException failure = new PersistenceException(new SQLException(
                "duplicate key value violates unique constraint \"pk_work_role_selections\"\n  Detail: Key (user_id)=("
                        + ROW_VALUE + ") already exists.",
                "23505",
                0));

        assertThat(classify(failure)).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));
    }

    @Test
    @DisplayName("a lost upsert is the unexpected path and its exception text carries no row value")
    void lostUpsertIsUnexpected() {
        KeyedUpsertLostException failure = new KeyedUpsertLostException(KeyedUpsert.WORK_ROLE_SELECTIONS);

        assertThat(RoleStoreClassifier.classify(failure)).isEqualTo(RoleStoreClassifier.Kind.UNEXPECTED);
        assertThatThrownBy(() -> classify(failure))
                .isInstanceOf(RoleStoreUnexpectedException.class)
                .satisfies(thrown ->
                        assertThat(String.valueOf(thrown.getMessage())).doesNotContain(ROW_VALUE));
    }
}
