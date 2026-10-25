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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * MySQL・MariaDB の行の SQL の方言（BR5.5・BR7.3・BR7.7・BR7.9）。時差つきの型（{@code timestamp}）は、接続を作るときの初期化の文
 * （{@code connectionInitSql} の {@code SET time_zone = '+00:00'}。読み取りのプールと書き込みのプールの両方。U2 の D5: A）の上で、UTC の
 * 時差の無い日時として読み書きする（T1）。
 */
public final class MysqlFamilyRowDialect implements RowDialect {

    /** MySQL の方言。 */
    static final MysqlFamilyRowDialect MYSQL = new MysqlFamilyRowDialect(DatabaseProduct.MYSQL, Set.of("json"));

    /** MariaDB の方言（{@code json} も文字で比べられる。T2）。 */
    static final MysqlFamilyRowDialect MARIADB = new MysqlFamilyRowDialect(DatabaseProduct.MARIADB, Set.of());

    private final DatabaseProduct product;

    private final Set<String> noEqualityTypes;

    private MysqlFamilyRowDialect(DatabaseProduct product, Set<String> noEqualityTypes) {
        this.product = product;
        this.noEqualityTypes = noEqualityTypes;
    }

    @Override
    public DatabaseProduct product() {
        return product;
    }

    @Override
    public String insertWithoutValues(String table) {
        return "INSERT INTO " + table + " () VALUES ()";
    }

    @Override
    public boolean readsGeneratedKeysByName() {
        return false;
    }

    @Override
    public String orderTerm(String columnRef, SortDirection direction) {
        // 「NULL か」のキーを先に置く（昇順は NULL が最後、降順は NULL が最初）。
        return direction == SortDirection.ASC
                ? columnRef + " IS NULL ASC, " + columnRef + " ASC"
                : columnRef + " IS NULL DESC, " + columnRef + " DESC";
    }

    @Override
    public String comparisonTarget(String columnRef, ValueType valueType) {
        return columnRef;
    }

    @Override
    public String castToText(String columnRef) {
        return "CAST(" + columnRef + " AS CHAR)";
    }

    @Override
    public void bindOther(PreparedStatement statement, int index, String value) throws SQLException {
        statement.setString(index, value);
    }

    @Override
    public void bindDateTimeTz(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException {
        statement.setObject(index, value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    @Override
    public OffsetDateTime readDateTimeTz(ResultSet resultSet, int index) throws SQLException {
        // 接続を作るときに time_zone を +00:00 にしているため、サーバーが送る文字は UTC の日時（ゼロの日時は読めずに例外になる）。
        String text = resultSet.getString(index);
        return text == null ? null : ColumnValueCodec.parseLocalDateTime(text).atOffset(ZoneOffset.UTC);
    }

    @Override
    public boolean readsTemporalAsText() {
        return true;
    }

    @Override
    public boolean readsBooleanAsInteger() {
        return true;
    }

    @Override
    public boolean equalsSupported(String dbTypeName) {
        return !noEqualityTypes.contains(RowDialect.baseName(dbTypeName));
    }
}
