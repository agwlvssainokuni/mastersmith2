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
package cherry.mastersmith.mail.template;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.mail.testsupport.MailTestTemplates;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * すべてのテンプレートの検査（BR2.4・BR2.6、AC3.1.4〜AC3.1.6、security-design.md の 7節、team.md のメールの必須のテスト）。
 *
 * <p>本番の一覧と置き場（{@code mail/templates/}）とテスト用の一覧と置き場を数え上げて調べる。B1 では本番の置き場は0件だが、
 * B3 で U3 が招待のテンプレート（invitation）と一覧の行を足すと、自動でこの検査の対象になる。
 */
class MailTemplateLintTest {

    private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

    /** エスケープを確かめる値（5つの文字を含む）。 */
    private static final String DANGEROUS = "<b x='1' y=\"2\">&";

    /** 差し込みのタグ（コメント・区間・通常）。 */
    private static final Pattern TAG = Pattern.compile("\\{\\{([#^/!]?)\\s*([^}]*?)\\s*\\}\\}", Pattern.DOTALL);

    /** HTML の開始タグ。 */
    private static final Pattern HTML_TAG = Pattern.compile("<[^!/][^>]*>");

    record Catalog(String label, List<MailTemplateDefinition> definitions, String location) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<Catalog> CATALOGS = List.of(
            new Catalog("production", MailTemplateCatalog.DEFINITIONS, MailTemplateCatalog.LOCATION),
            new Catalog("test", MailTestTemplates.CATALOG, MailTestTemplates.LOCATION));

    static Stream<Arguments> templates() {
        return CATALOGS.stream()
                .flatMap(catalog -> catalog.definitions().stream()
                        .flatMap(definition -> MailTemplateRegistry.LANGUAGES.stream()
                                .map(language -> Arguments.of(catalog, definition, language))));
    }

    private static String source(Catalog catalog, MailTemplateDefinition definition, String language)
            throws IOException {
        Resource resource =
                RESOLVER.getResource(catalog.location() + definition.templateId() + "_" + language + ".html");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MailTemplateRegistry registry(Catalog catalog) {
        return MailTemplateRegistry.prepare(catalog.definitions(), catalog.location(), RESOLVER);
    }

    @Test
    @DisplayName("every file in each location belongs to its catalog and the production location is empty in B1")
    void filesMatchTheCatalogs() throws IOException {
        for (Catalog catalog : CATALOGS) {
            Resource[] files;
            try {
                files = RESOLVER.getResources(catalog.location() + "*.html");
            } catch (java.io.FileNotFoundException e) {
                // 置き場のディレクトリが無い（B1 の本番の置き場）は0件として扱う。
                files = new Resource[0];
            }
            assertThat(files).hasSize(catalog.definitions().size() * MailTemplateRegistry.LANGUAGES.size());
        }
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("templates")
    @DisplayName("no unescaped, partial or delimiter-changing tag is used")
    void noForbiddenTags(Catalog catalog, MailTemplateDefinition definition, String language) throws IOException {
        String source = source(catalog, definition, language);

        assertThat(source)
                .doesNotContain("{{{")
                .doesNotContain("{{&")
                .doesNotContain("{{>")
                .doesNotContain("{{=");
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("templates")
    @DisplayName("every interpolation inside a tag is within a double-quoted attribute value")
    void attributeInterpolationsAreDoubleQuoted(Catalog catalog, MailTemplateDefinition definition, String language)
            throws IOException {
        Matcher tags = HTML_TAG.matcher(source(catalog, definition, language));
        while (tags.find()) {
            String tag = tags.group();
            int at = tag.indexOf("{{");
            while (at >= 0) {
                String before = tag.substring(0, at);
                long quotes = before.chars().filter(c -> c == '"').count();
                assertThat(quotes % 2).as("属性の値の差し込みが二重引用符で囲まれていない: %s", tag).isEqualTo(1);
                assertThat(before).as("属性の値の差し込みが二重引用符で囲まれていない: %s", tag).endsWith("\"");
                at = tag.indexOf("{{", at + 2);
            }
        }
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("templates")
    @DisplayName("the variable names used in the template equal the catalog")
    void variableNamesMatchTheCatalog(Catalog catalog, MailTemplateDefinition definition, String language)
            throws IOException {
        Set<String> used = new HashSet<>();
        Matcher matcher = TAG.matcher(source(catalog, definition, language));
        while (matcher.find()) {
            if (!matcher.group(1).equals("!") && !matcher.group(1).equals("/")) {
                used.add(matcher.group(2));
            }
        }

        assertThat(used).isEqualTo(definition.variableNames());
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("templates")
    @DisplayName("dangerous characters in every value are escaped and the rendered mail is complete")
    void renderedMailIsSafeAndComplete(Catalog catalog, MailTemplateDefinition definition, String language) {
        Map<String, String> values = new HashMap<>();
        definition.variableNames().forEach(name -> values.put(name, DANGEROUS + name));

        String html = registry(catalog)
                .render(definition.templateId(), language, values)
                .orElseThrow();
        RenderedMail mail = RenderedMailInspector.inspect(definition.templateId(), language, html)
                .orElseThrow();

        assertThat(html)
                .doesNotContain("<b ")
                .doesNotContain("y=\"2\"")
                .doesNotContain("{{")
                .doesNotContain("Licensed");
        definition
                .variableNames()
                .forEach(name -> assertThat(html).contains("&lt;b x='1' y=&quot;2&quot;&gt;&amp;" + name));
        assertThat(mail.subject()).isNotBlank().doesNotContain("{{").doesNotContain("Licensed");
        assertThat(RenderedMailInspector.lang(html)).contains(language);
    }
}
