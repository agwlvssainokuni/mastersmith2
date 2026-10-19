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
package cherry.mastersmith.targetdb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 業務データの書き込みの口が貸す組（Intent 261016-business-data-views の U2、BR9.3）の単体テスト。 */
class TargetWriteSessionTest {

    private static final SavepointMarker NO_OP = () -> {};

    @Test
    @DisplayName("the string form has the product and the seconds only, never the connection")
    void stringFormHidesTheConnection() {
        Connection connection = mock(Connection.class);
        when(connection.toString()).thenReturn("jdbc:mysql://db.example.com:3306/app?user=writer");

        TargetWriteSession session = new TargetWriteSession(DatabaseProduct.MYSQL, connection, 5, NO_OP);

        assertThat(session.toString())
                .isEqualTo("TargetWriteSession[product=MYSQL, queryTimeoutSeconds=5]")
                .doesNotContain("jdbc")
                .doesNotContain("example.com")
                .doesNotContain("writer");
    }

    @Test
    @DisplayName("marking a savepoint goes to the marker given by the access")
    void markSavepointDelegates() throws SQLException {
        AtomicInteger marks = new AtomicInteger();
        TargetWriteSession session =
                new TargetWriteSession(DatabaseProduct.POSTGRESQL, mock(Connection.class), 5, marks::incrementAndGet);

        session.markSavepoint();
        session.markSavepoint();

        assertThat(marks).hasValue(2);
    }

    @Test
    @DisplayName("a failure to mark a savepoint reaches the caller as an SQL failure")
    void markFailurePropagates() {
        SQLException failure = new SQLException(null, "08006");
        TargetWriteSession session = new TargetWriteSession(DatabaseProduct.MARIADB, mock(Connection.class), 5, () -> {
            throw failure;
        });

        assertThatThrownBy(session::markSavepoint).isSameAs(failure);
    }

    @Test
    @DisplayName("the product, the connection and the marker are required and the limit is at least one second")
    void requiredValues() {
        Connection connection = mock(Connection.class);

        assertThatThrownBy(() -> new TargetWriteSession(null, connection, 5, NO_OP))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetWriteSession(DatabaseProduct.MYSQL, null, 5, NO_OP))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetWriteSession(DatabaseProduct.MYSQL, connection, 5, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetWriteSession(DatabaseProduct.MYSQL, connection, 0, NO_OP))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TargetWriteSession(DatabaseProduct.MYSQL, connection, 1, NO_OP).queryTimeoutSeconds())
                .isEqualTo(1);
    }
}
