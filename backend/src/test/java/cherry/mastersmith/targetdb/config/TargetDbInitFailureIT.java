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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.service.JdbcTargetReadAccess;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLTransientConnectionException;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 接続の初期化の失敗の結合テスト（SD1.4）。本番の設定を作る部品が返す Hikari の設定のうち {@code connectionInitSql} だけを必ず失敗する
 * 文に差し替えたプールをテストの中で作る（本番のコードに口を足さない）。借りる失敗になり、アクセスの分類が {@code UNAVAILABLE} で、
 * 例外の文言に接続先・ユーザー名・パスワードが入っても、分類の結果と文字列には出ない。
 */
class TargetDbInitFailureIT {

    @Test
    @DisplayName("a failing init statement makes the borrow fail and the classification is UNAVAILABLE without secrets")
    void initFailureIsUnavailable() {
        DatabaseProduct product = DatabaseProduct.POSTGRESQL;
        TargetDbTestDatabase db = TargetDbTestDatabase.of(product);
        db.start();
        String schema = TargetDbTestDatabase.uniqueName("initfail_");
        String reader = TargetDbTestDatabase.uniqueName("reader_");
        String password = TestDatabase.randomSecret();
        try {
            db.createSchema(schema);
            db.createReadOnlyAccount(reader, password, schema);
            TargetDbProperties properties = new TargetDbProperties(
                    product.name().toLowerCase(Locale.ROOT),
                    db.host(),
                    String.valueOf(db.port()),
                    db.connectDatabase(schema),
                    schema,
                    reader,
                    password,
                    null,
                    null,
                    null,
                    null,
                    null);
            TargetDbSettings.Usable usable = (TargetDbSettings.Usable) TargetDbSettings.inspect(properties);
            HikariConfig config = TargetDataSourceConfig.hikariConfig(usable);
            assertThat(config.getConnectionInitSql()).isEqualTo("SET TIME ZONE 'UTC'");
            config.setConnectionInitSql("SELECT * FROM table_that_does_not_exist_for_init_failure");
            config.setConnectionTimeout(1_000L);

            try (HikariDataSource pool = new HikariDataSource(config)) {
                assertThatThrownBy(pool::getConnection).isInstanceOf(SQLTransientConnectionException.class);

                // 読み取りのアクセスを通すと、借りる失敗は UNAVAILABLE になり、結果の文字列に接続情報が出ない。
                JdbcTargetReadAccess access = new JdbcTargetReadAccess(usable, Optional.of(pool));
                TargetReadOutcome<String> outcome = access.withRead(ReadPurpose.LIST, session -> "never");
                assertThat(outcome).isInstanceOf(TargetReadOutcome.Unavailable.class);
                assertThat(outcome.toString())
                        .doesNotContain(password)
                        .doesNotContain(reader)
                        .doesNotContain(db.host());
            }
        } finally {
            db.dropAccount(reader);
            db.dropSchema(schema);
        }
    }
}
