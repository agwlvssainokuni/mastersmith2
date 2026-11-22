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
package cherry.mastersmith.internaldb.platform;

import java.util.Optional;

/**
 * 内部DB の種類（ENT-001）。種類ごとに、接続先の URL の先頭・移行の置き場・接続のたびに流す文を持つ。
 *
 * <p>内部DB の接続先の URL（{@code spring.datasource.url}）の先頭だけで種類を決める（{@link InternalDbUrlResolver}）。
 */
public enum InternalDbKind {

    /** 組み込みの H2（既定）。移行は {@code db/migration}。接続のたびの文は無い。 */
    H2("jdbc:h2:", "classpath:db/migration", null),

    /** PostgreSQL。移行は {@code db/migration-postgresql}。接続のたびに待ちの上限（{@code lock_timeout}）を 2 秒にする。 */
    POSTGRESQL("jdbc:postgresql:", "classpath:db/migration-postgresql", "SET lock_timeout = '2s'");

    private final String urlPrefix;
    private final String migrationLocation;
    private final String connectionInitSql;

    InternalDbKind(String urlPrefix, String migrationLocation, String connectionInitSql) {
        this.urlPrefix = urlPrefix;
        this.migrationLocation = migrationLocation;
        this.connectionInitSql = connectionInitSql;
    }

    /**
     * 接続先の URL の先頭（大文字と小文字を区別する）。
     *
     * @return URL の先頭
     */
    public String urlPrefix() {
        return urlPrefix;
    }

    /**
     * Flyway の移行の置き場（クラスパスの場所）。
     *
     * @return 移行の置き場
     */
    public String migrationLocation() {
        return migrationLocation;
    }

    /**
     * 接続のたびに流す文（定数。利用者の入力を含まない）。無い種類は空。
     *
     * @return 接続のたびの文
     */
    public Optional<String> connectionInitSql() {
        return Optional.ofNullable(connectionInitSql);
    }
}
