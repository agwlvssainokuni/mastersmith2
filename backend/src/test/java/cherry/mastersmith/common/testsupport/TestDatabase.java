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
package cherry.mastersmith.common.testsupport;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.test.context.DynamicPropertyRegistry;

/** 結合テストで、一時ディレクトリの組み込みの H2（ファイル保存）を使うためのテストの補助。 */
public final class TestDatabase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestDatabase() {}

    /**
     * 指定したディレクトリの H2 のファイルの接続 URL を返す。
     *
     * @param dir H2 のファイルを置くディレクトリ
     * @return 接続 URL
     */
    public static String url(Path dir) {
        return "jdbc:h2:file:" + dir.toAbsolutePath().resolve("mastersmith");
    }

    /**
     * テストのクラスの内部DBの接続先を、指定したディレクトリの H2 のファイルにする。
     *
     * @param registry Spring のテストの設定の登録先
     * @param dir H2 のファイルを置くディレクトリ
     */
    public static void register(DynamicPropertyRegistry registry, Path dir) {
        registry.add("spring.datasource.url", () -> url(dir));
    }

    /**
     * 秘密情報の役のテストの値（乱数の16進数の文字列）を作る。ソースに本物らしい秘密情報を書かないために使う。
     *
     * @return 乱数の文字列
     */
    public static String randomSecret() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "t" + HexFormat.of().formatHex(bytes);
    }
}
