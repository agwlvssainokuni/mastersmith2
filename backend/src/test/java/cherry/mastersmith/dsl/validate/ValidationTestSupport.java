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
package cherry.mastersmith.dsl.validate;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.parse.YamlDocument;
import cherry.mastersmith.dsl.parse.YamlParseResult;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 検証のテストの補助。 */
final class ValidationTestSupport {

    private ValidationTestSupport() {}

    static YamlDocument document(String yaml) {
        YamlParseResult result = new SafeYamlParser().parse(yaml.getBytes(StandardCharsets.UTF_8));
        assertThat(result).isInstanceOf(YamlParseResult.Parsed.class);
        return ((YamlParseResult.Parsed) result).document();
    }

    /** 誤りが文言の鍵の一覧の鍵だけを使い、部品の文言（例外のクラス名・networknt・SnakeYAML の説明文）を含まないことを確かめる。 */
    static void assertNoComponentText(List<DslError> errors) {
        for (DslError error : errors) {
            assertThat(DslMessageKeys.all()).contains(error.messageKey());
            String text = String.join("\n", error.messageArgs());
            assertThat(text)
                    .doesNotContain(
                            "Exception",
                            "networknt",
                            "snakeyaml",
                            "must be",
                            "is not defined",
                            "required property",
                            "$.");
        }
    }
}
