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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 標準出力に出た1行1件の JSON のログを読み、項目を取り出すためのテストの補助。
 *
 * <p>Spring Boot の {@code OutputCaptureExtension} で捕まえた出力を渡して使う。U2 以降のテストも、ログに秘密情報
 * （パスワード・トークン・署名鍵）の値が出ないことをこの補助で確かめる（NFR3.15）。
 */
public final class JsonLogRecords {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonLogRecords() {}

    /**
     * 出力を行に分け、JSON のログの1件ずつを項目の対応表にする。JSON でない行は含めない。
     *
     * @param output 捕まえた標準出力
     * @return ログの各件の項目
     */
    public static List<Map<String, Object>> parse(String output) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : output.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                records.add(MAPPER.readValue(trimmed, new TypeReference<Map<String, Object>>() {}));
            }
        }
        return records;
    }

    /**
     * 出力の空でない行が、すべて1行1件の JSON であることを確かめる。
     *
     * @param output 捕まえた標準出力
     */
    public static void assertAllLinesAreJson(String output) {
        for (String line : output.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.strip();
            assertThat(trimmed)
                    .as("ログの行が JSON でない: %s", trimmed)
                    .startsWith("{")
                    .endsWith("}");
            MAPPER.readTree(trimmed);
        }
    }

    /**
     * 指定した秘密の値が、出力のどこにも含まれないことを確かめる。
     *
     * @param output 捕まえた標準出力
     * @param secrets 出てはならない値
     */
    public static void assertContainsNoSecret(String output, String... secrets) {
        for (String secret : secrets) {
            assertThat(secret).as("秘密の値が空では確かめられない").isNotBlank();
            assertThat(output).as("ログに秘密の値が含まれている").doesNotContain(secret);
        }
    }
}
