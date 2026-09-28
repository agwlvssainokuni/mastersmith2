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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.mail.template.TemplatePreparationException.Reason;
import cherry.mastersmith.mail.testsupport.MailTestTemplates;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** テンプレートの準備と描画の単体テスト（BR2.1〜BR2.3・BR4.1・BR4.4、NFR6.5、NFR8.1）。 */
class MailTemplateRegistryTest {

    private static final List<MailTemplateDefinition> SAMPLE_ONLY =
            List.of(new MailTemplateDefinition("sample", Set.of("name", "link")));

    private static MailTemplateRegistry prepare(String dir) {
        return MailTemplateRegistry.prepare(
                SAMPLE_ONLY,
                "classpath:mail/test-templates-invalid/" + dir + "/",
                new PathMatchingResourcePatternResolver());
    }

    @Test
    @DisplayName("both languages are prepared and each request renders only with its own language")
    void rendersOnlyWithTheRequestedLanguage() {
        MailTemplateRegistry registry = MailTestTemplates.registry();
        Map<String, String> values = Map.of("name", "山田", "link", "https://example.com/a");

        String ja = registry.render("sample", "ja", values).orElseThrow();
        String en = registry.render("sample", "en", values).orElseThrow();

        assertThat(ja).contains("<html lang=\"ja\">").contains("山田 さん、こんにちは。").doesNotContain("Hello");
        assertThat(en).contains("<html lang=\"en\">").contains("Hello, 山田.").doesNotContain("こんにちは");
        assertThat(registry.preparedTemplateNames())
                .containsExactly("multiline_en", "multiline_ja", "sample_en", "sample_ja");
    }

    @Test
    @DisplayName("the license header comment does not appear in the rendered body")
    void headerCommentIsNotRendered() {
        String html = MailTestTemplates.registry()
                .render("sample", "ja", Map.of("name", "a", "link", "b"))
                .orElseThrow();

        assertThat(html)
                .startsWith("<!DOCTYPE html>")
                .doesNotContain("Licensed")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("an unknown template or language renders nothing")
    void unknownTemplateOrLanguage() {
        MailTemplateRegistry registry = MailTestTemplates.registry();

        assertThat(registry.render("other", "ja", Map.of())).isEmpty();
        assertThat(registry.render("sample", "fr", Map.of())).isEmpty();
        assertThat(registry.render(null, null, Map.of())).isEmpty();
        assertThat(registry.contains(null)).isFalse();
        assertThat(registry.variableNames("sample")).contains(Set.of("name", "link"));
        assertThat(registry.variableNames("other")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "missing-en, sample, en, MISSING",
        "parse-error, sample, en, PARSE_ERROR",
        "unknown-template, other, ja, UNKNOWN_TEMPLATE"
    })
    @DisplayName("preparation fails with only the template id, the language and the reason")
    void preparationFailures(String dir, String templateId, String language, Reason reason) {
        assertThatThrownBy(() -> prepare(dir)).isInstanceOfSatisfying(TemplatePreparationException.class, e -> {
            assertThat(e.reason()).isEqualTo(reason);
            assertThat(e.templateId()).isEqualTo(templateId);
            assertThat(e.language()).isEqualTo(language);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain("{{").doesNotContain("href");
        });
    }

    @Test
    @DisplayName("a file whose name does not match the naming rule stops the preparation")
    void invalidFileName() {
        assertThatThrownBy(() -> prepare("invalid-name"))
                .isInstanceOfSatisfying(
                        TemplatePreparationException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_NAME));
    }

    @Test
    @DisplayName("an empty catalog is prepared even when the location is missing or has no files")
    void emptyCatalog() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        assertThat(MailTemplateRegistry.prepare(List.of(), "classpath:mail/no-such-dir/", resolver)
                        .preparedTemplateNames())
                .isEmpty();
        assertThat(MailTemplateRegistry.prepare(List.of(), "classpath:mail/", resolver)
                        .preparedTemplateNames())
                .isEmpty();
    }

    @Test
    @DisplayName("the production catalog prepares the invitation template in ja and en (added in B3)")
    void productionCatalog() {
        MailTemplateRegistry registry = MailTemplateRegistry.prepare(
                MailTemplateCatalog.DEFINITIONS,
                MailTemplateCatalog.LOCATION,
                new PathMatchingResourcePatternResolver());

        assertThat(registry.preparedTemplateNames()).containsExactly("invitation_en", "invitation_ja");
        assertThat(registry.variableNames("invitation")).contains(Set.of("registrationUrl", "validityHours"));
    }

    @Test
    @DisplayName("a duplicated template id in the catalog is rejected")
    void duplicatedTemplateId() {
        List<MailTemplateDefinition> catalog = List.of(
                new MailTemplateDefinition("sample", Set.of()), new MailTemplateDefinition("sample", Set.of("name")));

        assertThatThrownBy(() -> MailTemplateRegistry.prepare(
                        catalog, MailTestTemplates.LOCATION, new PathMatchingResourcePatternResolver()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the same prepared template rendered by many threads gives consistent results")
    void concurrentRendering() throws Exception {
        MailTemplateRegistry registry = MailTestTemplates.registry();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String name = "user" + i;
                results.add(executor.submit(() -> registry.render(
                                "sample", i2(name), Map.of("name", name, "link", "https://example.com/" + name))
                        .orElseThrow()
                        .contains("https://example.com/" + name + "\" title=\"" + name + "\"")));
            }
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static String i2(String name) {
        return name.hashCode() % 2 == 0 ? "ja" : "en";
    }

    @Test
    @DisplayName("the definition rejects malformed template ids and variable names")
    void definitionValidation() {
        assertThatThrownBy(() -> new MailTemplateDefinition("Sample", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailTemplateDefinition("sample_x", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailTemplateDefinition("sample", Set.of("1name")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
