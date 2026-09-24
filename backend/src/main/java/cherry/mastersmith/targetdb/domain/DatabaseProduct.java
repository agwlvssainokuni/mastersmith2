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

import java.util.Locale;
import java.util.Optional;

/** 対象DB の種類（BR2.7）。識別子を囲む引用符は種類ごとに決まる（NFR6.1）。 */
public enum DatabaseProduct {
    /** MySQL。 */
    MYSQL('`'),
    /** MariaDB。 */
    MARIADB('`'),
    /** PostgreSQL。 */
    POSTGRESQL('"');

    private final char identifierQuote;

    DatabaseProduct(char identifierQuote) {
        this.identifierQuote = identifierQuote;
    }

    /**
     * 識別子を囲む引用符を返す。
     *
     * @return MySQL・MariaDB は {@code `}、PostgreSQL は {@code "}
     */
    public char identifierQuote() {
        return identifierQuote;
    }

    /**
     * 設定の値（{@code mysql}・{@code mariadb}・{@code postgresql}。大文字・小文字は問わない）から種類を得る。
     *
     * @param value 設定の値
     * @return 種類（対応外・空なら空）
     */
    public static Optional<DatabaseProduct> fromSetting(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        for (DatabaseProduct product : values()) {
            if (product.name().equals(normalized)) {
                return Optional.of(product);
            }
        }
        return Optional.empty();
    }
}
