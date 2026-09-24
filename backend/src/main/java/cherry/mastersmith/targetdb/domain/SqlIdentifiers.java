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

import java.util.Collection;
import java.util.Objects;

/**
 * 識別子を SQL に組み込むときの、唯一の入り口（BR2.4、NFR6.1）。
 *
 * <p>今の読み取りの問い合わせは固定の文で識別子を組み込まないため使わない。後で識別子を組み込む問い合わせが要るときは、
 * 読み取った名前の一覧にあるものだけを {@link #quoteKnown} で囲む。利用者の入力から識別子を作らない。
 */
public final class SqlIdentifiers {

    private SqlIdentifiers() {}

    /**
     * 識別子を DB の種類に合った引用符で囲む。中の引用符は二重にする。
     *
     * @param product DB の種類
     * @param identifier 識別子（空・NUL を含むものは受け付けない）
     * @return 囲んだ識別子
     */
    public static String quote(DatabaseProduct product, String identifier) {
        Objects.requireNonNull(product, "product は必須です");
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("識別子は必須です");
        }
        if (identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("識別子に NUL は使えません");
        }
        char quote = product.identifierQuote();
        StringBuilder quoted = new StringBuilder(identifier.length() + 2).append(quote);
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (c == quote) {
                quoted.append(quote);
            }
            quoted.append(c);
        }
        return quoted.append(quote).toString();
    }

    /**
     * 読み取った名前の一覧にある識別子だけを、DB の種類に合った引用符で囲む（BR2.4）。
     *
     * @param product DB の種類
     * @param identifier 識別子
     * @param knownNames 読み取った名前の一覧
     * @return 囲んだ識別子
     * @throws IllegalArgumentException 一覧に無いとき（問い合わせをせずに想定外の失敗とする）
     */
    public static String quoteKnown(DatabaseProduct product, String identifier, Collection<String> knownNames) {
        Objects.requireNonNull(knownNames, "knownNames は必須です");
        if (!knownNames.contains(identifier)) {
            throw new IllegalArgumentException("読み取った名前の一覧に無い識別子は使えません");
        }
        return quote(product, identifier);
    }
}
