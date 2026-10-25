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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 業務データの読み取りの口が貸す組（BR8.1・BR10.1）の単体テスト。 */
class TargetReadSessionTest {

    @Test
    @DisplayName("the string form has the product and the seconds only, never the connection")
    void stringFormHidesTheConnection() {
        Connection connection = mock(Connection.class);
        when(connection.toString()).thenReturn("jdbc:postgresql://db.example.com:5432/app?user=reader");

        TargetReadSession session = new TargetReadSession(DatabaseProduct.POSTGRESQL, connection, 5);

        assertThat(session.toString())
                .isEqualTo("TargetReadSession[product=POSTGRESQL, queryTimeoutSeconds=5]")
                .doesNotContain("jdbc")
                .doesNotContain("example.com")
                .doesNotContain("reader");
    }

    @Test
    @DisplayName("the components are kept as given")
    void keepsTheComponents() {
        Connection connection = mock(Connection.class);

        TargetReadSession session = new TargetReadSession(DatabaseProduct.MYSQL, connection, 1);

        assertThat(session.product()).isEqualTo(DatabaseProduct.MYSQL);
        assertThat(session.connection()).isSameAs(connection);
        assertThat(session.queryTimeoutSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("the broken mark starts down, goes up once marked and stays out of the string form")
    void brokenMark() {
        TargetReadSession session = new TargetReadSession(DatabaseProduct.MYSQL, mock(Connection.class), 5);

        assertThat(session.isBroken()).isFalse();
        session.markBroken();
        assertThat(session.isBroken()).isTrue();
        assertThat(session.toString()).isEqualTo("TargetReadSession[product=MYSQL, queryTimeoutSeconds=5]");
    }

    @Test
    @DisplayName("the product is required")
    void productIsRequired() {
        assertThatThrownBy(() -> new TargetReadSession(null, mock(Connection.class), 5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the connection is required")
    void connectionIsRequired() {
        assertThatThrownBy(() -> new TargetReadSession(DatabaseProduct.MARIADB, null, 5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the query timeout must be at least one second")
    void timeoutBoundary() {
        Connection connection = mock(Connection.class);

        assertThatThrownBy(() -> new TargetReadSession(DatabaseProduct.MARIADB, connection, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TargetReadSession(DatabaseProduct.MARIADB, connection, 1).queryTimeoutSeconds())
                .isEqualTo(1);
    }
}
