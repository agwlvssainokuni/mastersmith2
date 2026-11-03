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
package cherry.mastersmith.targetdb.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 接続のタイムゾーンの結合テスト（3種類の対象DB の実物。BR3.1・BR3.2・BR3.17）。読み取りと書き込みの両方のプールで、接続のセッションが
 * UTC になり、テストの JVM の既定のタイムゾーンを変えても日時の読み取りが変わらないことを確かめる（K-95）。
 */
class TargetDbTimeZoneIT {

    private static TargetDbProperties properties(
            TargetDbTestDatabase db,
            String schema,
            String reader,
            String readerPassword,
            String writer,
            String writerPassword) {
        String type = db.product().name().toLowerCase(java.util.Locale.ROOT);
        return new TargetDbProperties(
                type,
                db.host(),
                String.valueOf(db.port()),
                db.connectDatabase(schema),
                schema,
                reader,
                readerPassword,
                null,
                null,
                null,
                null,
                new TargetDbProperties.Write(writer, writerPassword, null));
    }

    private static String sessionZone(DatabaseProduct product, Connection connection) throws Exception {
        String sql = product == DatabaseProduct.POSTGRESQL ? "SHOW TimeZone" : "SELECT @@session.time_zone";
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @ParameterizedTest
    @EnumSource(DatabaseProduct.class)
    @DisplayName("the read and write pools run the session in UTC once per connection and the JVM zone does not matter")
    void sessionsAreUtc(DatabaseProduct product) throws Exception {
        TargetDbTestDatabase db = TargetDbTestDatabase.of(product);
        db.start();
        String schema = TargetDbTestDatabase.uniqueName("tz_");
        String reader = TargetDbTestDatabase.uniqueName("reader_");
        String writer = TargetDbTestDatabase.uniqueName("writer_");
        String readerPassword = TestDatabase.randomSecret();
        String writerPassword = TestDatabase.randomSecret();
        TimeZone original = TimeZone.getDefault();
        try {
            db.createSchema(schema);
            db.createReadOnlyAccount(reader, readerPassword, schema);
            db.createWriterAccount(writer, writerPassword, schema);
            TargetDbProperties properties = properties(db, schema, reader, readerPassword, writer, writerPassword);
            TargetDbSettings.Usable usable = (TargetDbSettings.Usable) TargetDbSettings.inspect(properties);
            TargetDbWriteSettings.Usable writeUsable =
                    (TargetDbWriteSettings.Usable) TargetDbWriteSettings.inspect(usable, properties);
            String expected = product == DatabaseProduct.POSTGRESQL ? "UTC" : "+00:00";

            for (String jvmZone : new String[] {"Asia/Tokyo", "America/Los_Angeles", "UTC"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
                HikariConfig read = TargetDataSourceConfig.hikariConfig(usable);
                HikariConfig write = TargetWriteDataSourceConfig.hikariConfig(writeUsable);
                try (HikariDataSource readPool = new HikariDataSource(read);
                        HikariDataSource writePool = new HikariDataSource(write)) {
                    for (HikariDataSource pool : new HikariDataSource[] {readPool, writePool}) {
                        // 同じ接続を続けて借りても、初期化の文は接続を作るときの1回だけ（セッションの設定が保たれる）。
                        for (int i = 0; i < 2; i++) {
                            try (Connection connection = pool.getConnection()) {
                                assertThat(sessionZone(product, connection)).isEqualTo(expected);
                            }
                        }
                    }
                    assertThat(readPool.getHikariPoolMXBean().getTotalConnections())
                            .isEqualTo(1);
                    if (product == DatabaseProduct.POSTGRESQL) {
                        try (Connection connection = readPool.getConnection();
                                Statement statement = connection.createStatement();
                                ResultSet rs = statement.executeQuery("SELECT TIMESTAMPTZ '2026-03-08 01:30:00+00',"
                                        + " TIMESTAMP '2026-03-08 01:30:00'")) {
                            rs.next();
                            assertThat(rs.getObject(1, OffsetDateTime.class).toInstant())
                                    .as("JVM の既定は " + jvmZone)
                                    .isEqualTo(OffsetDateTime.of(2026, 3, 8, 1, 30, 0, 0, ZoneOffset.UTC)
                                            .toInstant());
                            assertThat(rs.getObject(2, LocalDateTime.class))
                                    .as("時差を持たない型は時差の前後で変わらない")
                                    .isEqualTo(LocalDateTime.of(2026, 3, 8, 1, 30));
                            assertThat(rs.getString(1)).isEqualTo("2026-03-08 01:30:00+00");
                        }
                    }
                }
            }
        } finally {
            TimeZone.setDefault(original);
            db.dropAccount(reader);
            db.dropAccount(writer);
            db.dropSchema(schema);
        }
    }
}
