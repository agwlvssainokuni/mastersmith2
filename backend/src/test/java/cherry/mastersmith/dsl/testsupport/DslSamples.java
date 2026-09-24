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
package cherry.mastersmith.dsl.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** DSL のテストの見本（{@code src/test/resources/cherry/mastersmith/dsl/}）を読む補助。 */
public final class DslSamples {

    private static final String VALID = "cherry/mastersmith/dsl/valid-sample.yaml";

    private DslSamples() {}

    /**
     * 正しい見本の DSL を返す。
     *
     * @return YAML の本文
     */
    public static String validYaml() {
        try (InputStream in = DslSamples.class.getClassLoader().getResourceAsStream(VALID)) {
            if (in == null) {
                throw new IllegalStateException("見本が見つかりません: " + VALID);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 正しい見本の一部を置き換えた DSL を返す。置き換える文字が見本に無ければ失敗させる（見本の変更に気付くため）。
     *
     * @param target 置き換える文字
     * @param replacement 置き換えた後の文字
     * @return YAML の本文
     */
    public static String validYamlReplacing(String target, String replacement) {
        String yaml = validYaml();
        if (!yaml.contains(target)) {
            throw new IllegalArgumentException("見本に置き換える文字がありません: " + target);
        }
        return yaml.replace(target, replacement);
    }

    /**
     * 文字の行番号（1 から）を返す。
     *
     * @param yaml YAML の本文
     * @param text 探す文字（最初に現れる行）
     * @return 行番号
     */
    public static int lineOf(String yaml, String text) {
        int index = yaml.indexOf(text);
        if (index < 0) {
            throw new IllegalArgumentException("文字が見つかりません: " + text);
        }
        return (int) yaml.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }
}
