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
package cherry.mastersmith.internaldb.testsupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 利用者の検索（BR8.1）の見本の氏名と、検索語ごとに当たる氏名の固定の期待。期待は、本体の検索（小文字にそろえ {@code \}・{@code %}・{@code _} を
 * エスケープして {@code ilike}）の決まりから導き、H2 でも同じ結果になることを使い捨てのコードで確かめた値（Unicode の端の
 * {@code İ}・語末シグマ・{@code ǅ} は対象に入れない。U2 の Q3: A）。メールアドレスは見本ごとに {@code u<番号>@example.com} で、検索語に当たらない。
 */
public final class PgUserSearchSamples {

    /** 見本の氏名（氏名はどれも重ならない）。 */
    public static final List<String> NAMES = List.of(
            "山田 太郎",
            "山田　花子",
            "ＹＡＭＡＤＡ",
            "yamada",
            "YAMADA Taro",
            "ヤマダ",
            "やまだ",
            "100% done",
            "100 percent",
            "a_b",
            "axb",
            "a\\b",
            "ab",
            "mixed   spaces",
            "mixed spaces",
            "Straße",
            "STRASSE",
            "café",
            "CAFÉ",
            "!exclaim",
            "café combined");

    /** 検索語 → 当たる氏名の集合。 */
    public static final Map<String, Set<String>> CASES = cases();

    private PgUserSearchSamples() {}

    private static Map<String, Set<String>> cases() {
        Map<String, Set<String>> cases = new LinkedHashMap<>();
        cases.put("yamada", Set.of("yamada", "YAMADA Taro"));
        cases.put("YAMADA", Set.of("yamada", "YAMADA Taro"));
        cases.put("taro", Set.of("YAMADA Taro"));
        cases.put("TARO", Set.of("YAMADA Taro"));
        cases.put("山田", Set.of("山田 太郎", "山田　花子"));
        cases.put("太郎", Set.of("山田 太郎"));
        cases.put("ヤマダ", Set.of("ヤマダ"));
        cases.put("やまだ", Set.of("やまだ"));
        cases.put("ＹＡＭＡＤＡ", Set.of("ＹＡＭＡＤＡ"));
        cases.put("100%", Set.of("100% done"));
        cases.put("%", Set.of("100% done"));
        cases.put("100", Set.of("100% done", "100 percent"));
        cases.put("_", Set.of("a_b"));
        cases.put("a_b", Set.of("a_b"));
        cases.put("\\", Set.of("a\\b"));
        cases.put("a\\b", Set.of("a\\b"));
        cases.put("mixed spaces", Set.of("mixed spaces"));
        cases.put("mixed   spaces", Set.of("mixed   spaces"));
        cases.put("straße", Set.of("Straße"));
        cases.put("strasse", Set.of("STRASSE"));
        cases.put("café", Set.of("café", "CAFÉ"));
        cases.put("CAFÉ", Set.of("café", "CAFÉ"));
        cases.put("café", Set.of("café combined"));
        cases.put("!", Set.of("!exclaim"));
        cases.put("no-such-name", Set.of());
        return cases;
    }
}
