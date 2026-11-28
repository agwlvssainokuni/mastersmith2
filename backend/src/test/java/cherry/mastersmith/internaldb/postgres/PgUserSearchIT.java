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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PgUserSearchSamples;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 利用者の検索（BR8.1）。日本語・全角半角・カナ・{@code %}・{@code _}・{@code \}・大文字と小文字違い・連続空白・ß・結合文字などの見本の利用者に対し、
 * 検索の結果の集合が固定の期待（{@code PgUserSearchSamples}。H2 でも同じ結果になることを確かめた値）と同じで、検索語の {@code %}・{@code _}・{@code \} は
 * 一致の条件として働かない。Unicode の端の 3 つ（{@code İ}・語末シグマ・{@code ǅ}）は対象に入れない（U2 の Q3: A。U4 の文書に書く）。
 * 氏名は出力に出さない（失敗の文は検索語の長さと件数だけ）。
 */
@InternalDbPostgresIT
class PgUserSearchIT {

    private static PgApp app;

    private static PortableFixture fixture;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
        int number = 1;
        for (String name : PgUserSearchSamples.NAMES) {
            fixture.createUserNamed(String.format("u%02d@example.com", number++), name);
        }
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private static Set<String> search(String query) {
        HttpResponse<String> response = fixture.adminCall(
                "GET", "/api/admin/users?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), null);
        assertThat(response.statusCode())
                .as("search status for a query of %d code points", query.codePointCount(0, query.length()))
                .isEqualTo(200);
        Set<String> names = new HashSet<>();
        for (Object item : (List<?>) HttpTestClient.json(response).get("items")) {
            names.add((String) ((Map<?, ?>) item).get("displayName"));
        }
        return names;
    }

    @Test
    @DisplayName("every sample query returns exactly the expected set of users, and %, _ and backslash are literal")
    void queriesReturnTheFixedSets() {
        List<String> mismatches = new ArrayList<>();
        int number = 0;
        for (Map.Entry<String, Set<String>> entry : PgUserSearchSamples.CASES.entrySet()) {
            number++;
            Set<String> actual = search(entry.getKey());
            if (!actual.equals(entry.getValue())) {
                mismatches.add(
                        "query #" + number + " expected=" + entry.getValue().size() + " actual=" + actual.size());
            }
        }

        assertThat(PgUserSearchSamples.NAMES).hasSize(21);
        assertThat(mismatches)
                .as("queries whose result set differs (counts only)")
                .isEmpty();
    }

    @Test
    @DisplayName("a search of exactly 254 code points is accepted, and one of 255 is 400 TOO_LONG on q")
    void lengthLimit() {
        assertThat(search("x".repeat(254))).isEmpty();

        HttpResponse<String> tooLong = fixture.adminCall("GET", "/api/admin/users?q=" + "x".repeat(255), null);

        assertThat(tooLong.statusCode()).isEqualTo(400);
    }
}
