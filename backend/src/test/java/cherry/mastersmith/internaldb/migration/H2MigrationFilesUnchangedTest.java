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
package cherry.mastersmith.internaldb.migration;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.MigrationObjectNames;
import cherry.mastersmith.internaldb.testsupport.MigrationObjectNames.MigrationFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * H2 用の移行（{@code db/migration}）の V1〜V14 が変わっていないことの確かめ（FR2.2・BR2.1・BR2.8 の (a)(b)）。適用済みの移行を
 * 書き換えると、前の版が作った H2 のファイルの移行の検証が通らなくなる。
 */
class H2MigrationFilesUnchangedTest {

    /** 版の番号 → H2 用の移行のファイルの SHA-256（この Intent の開始時点の中身）。 */
    private static final Map<Integer, String> EXPECTED_SHA256 = Map.ofEntries(
            Map.entry(1, "4465d2ec521b03ab1891932a5694183533cbdfccbd82370499f761b2cc1e8579"),
            Map.entry(2, "f085d696cc031ab9584c4464b67e67313b502c2c98471f5866fab8cb414031d0"),
            Map.entry(3, "e7dc0f932a9896ab56cb77d46a9ebb3234475167eae0db1782f57c825dcc2345"),
            Map.entry(4, "71ee1b03e0b603daeb206aabc1b4617d67d49089f6102b405b89ff0d37085cf3"),
            Map.entry(5, "a99930b37d84f94b7fb3eeebe74276c2214151ee84071024d2fc3fa1b99535f7"),
            Map.entry(6, "2097da6b03cebfcefe319c284f419d111fff9d432d43e282a11cb30177c00ddc"),
            Map.entry(7, "c0a849d37405311e93f503de734a5cd8481104b0aa1124656490f97b6e4776cb"),
            Map.entry(8, "f8d7b8d0b3c1b23492f2badc3048af780a2579d1f24c180fa28e109e428c5b65"),
            Map.entry(9, "89b446d91bf89c83b2c4da6c7326eda4d95c55a779ab0e5872886babcb3cdfa5"),
            Map.entry(10, "281d984b9ade20b7814ac0f75e5fdeced4b7b763377a92d1dbfd06ebe3396914"),
            Map.entry(11, "c6aa69861152c61899edd7141ef546f360aaf13fb59842fe1fdb744ce257c160"),
            Map.entry(12, "b1d57117c0cd76f28b7c4da03927814ab446b986fd232b857e334829337eff93"),
            Map.entry(13, "82ed12263e8496527462eef1940279e3c772440def853ba2b573de4e9ca3c9cc"),
            Map.entry(14, "8cef811c8fc0000210a26ade6f237f6dc2534b4855a4bd485a3dbddb0c9cd761"));

    static List<Integer> versions() {
        return EXPECTED_SHA256.keySet().stream().sorted().toList();
    }

    @ParameterizedTest(name = "V{0} of the H2 migrations keeps its SHA-256")
    @MethodSource("versions")
    @DisplayName("each H2 migration file has the recorded SHA-256")
    void h2MigrationKeepsItsHash(int version) throws NoSuchAlgorithmException {
        SortedMap<Integer, MigrationFile> files = MigrationObjectNames.load("db/migration");

        MigrationFile file = files.get(version);

        assertThat(file).as("H2 migration V%d exists", version).isNotNull();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(file.sql().getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(digest)).isEqualTo(EXPECTED_SHA256.get(version));
    }

    @Test
    @DisplayName("scanning db/migration recursively finds only the 14 H2 files (no PostgreSQL files mixed in)")
    void h2LocationHoldsOnlyTheFourteenH2Files() {
        List<String> names = MigrationObjectNames.allSqlFileNames("db/migration");

        assertThat(names).hasSize(14);
        assertThat(MigrationObjectNames.load("db/migration").keySet()).containsExactlyElementsOf(versions());
        assertThat(names).doesNotHaveDuplicates();
    }
}
