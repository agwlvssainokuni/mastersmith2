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
package cherry.mastersmith.businessdata.repository;

import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/** PostgreSQL の行の SQL の方言（BR5.5・BR7.3・BR7.7・BR7.9）。 */
public final class PostgresRowDialect implements RowDialect {

    /** ただ1つの方言。 */
    static final PostgresRowDialect INSTANCE = new PostgresRowDialect();

    /** {@code =} を持たないため完全一致と主キーの指し方から外す型（T2。42883 になる）。 */
    static final Set<String> NO_EQUALITY_TYPES =
            Set.of("json", "xml", "point", "line", "lseg", "box", "path", "polygon", "circle");

    private PostgresRowDialect() {}

    @Override
    public DatabaseProduct product() {
        return DatabaseProduct.POSTGRESQL;
    }

    @Override
    public boolean needsUtcSession() {
        return false;
    }

    @Override
    public String orderTerm(String columnRef, SortDirection direction) {
        return direction == SortDirection.ASC ? columnRef + " ASC NULLS LAST" : columnRef + " DESC NULLS FIRST";
    }

    @Override
    public String comparisonTarget(String columnRef, ValueType valueType) {
        return valueType == ValueType.TIME_TZ ? "(" + columnRef + " AT TIME ZONE 'UTC')" : columnRef;
    }

    @Override
    public String castToText(String columnRef) {
        return "CAST(" + columnRef + " AS TEXT)";
    }

    @Override
    public void bindOther(PreparedStatement statement, int index, String value) throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }

    @Override
    public void bindDateTimeTz(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException {
        statement.setObject(index, value.withOffsetSameInstant(ZoneOffset.UTC));
    }

    @Override
    public OffsetDateTime readDateTimeTz(ResultSet resultSet, int index) throws SQLException {
        OffsetDateTime value = resultSet.getObject(index, OffsetDateTime.class);
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Override
    public boolean readsTemporalAsText() {
        return false;
    }

    @Override
    public boolean equalsSupported(String dbTypeName) {
        return !NO_EQUALITY_TYPES.contains(RowDialect.baseName(dbTypeName));
    }
}
