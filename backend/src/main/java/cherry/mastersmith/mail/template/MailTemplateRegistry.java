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

import cherry.mastersmith.mail.template.TemplatePreparationException.Reason;
import cherry.mustache.MapPartialResolver;
import cherry.mustache.Mustache;
import cherry.mustache.MustacheException;
import cherry.mustache.PartialResolver;
import cherry.mustache.Template;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * テンプレートの一覧と、起動のときに準備したテンプレート（BR2.1〜BR2.3、BR4.1、NFR6.5、NFR8.1）。
 *
 * <p>作るときに置き場の {@code *.html} を数え上げて名前と一覧を照らし、一覧の templateId ごとに ja・en を UTF-8 で読んで、空の部分
 * テンプレートの解決で準備する。欠け・壊れ・名前の誤り・一覧に無い templateId は {@link TemplatePreparationException} で失敗させ、
 * 起動を止める。準備したテンプレートは動いている間は変えない（同時に描いてよい）。
 */
public final class MailTemplateRegistry {

    /** 受け付ける言語（ファイル名の {@code _<language>}）。 */
    public static final List<String> LANGUAGES = List.of("ja", "en");

    /** 置き場のファイルの名前の形（{@code <templateId>_<language>.html}）。 */
    private static final Pattern FILE_NAME = Pattern.compile("([a-z][a-z0-9-]*)_(ja|en)\\.html");

    /** 部分テンプレートは使わない（空の解決。書いても黙って空になるため、テンプレートの検査で禁止する）。 */
    private static final PartialResolver NO_PARTIALS = new MapPartialResolver(Map.of());

    private final Map<String, MailTemplateDefinition> definitions;

    private final Map<String, Template> templates;

    private MailTemplateRegistry(Map<String, MailTemplateDefinition> definitions, Map<String, Template> templates) {
        this.definitions = Collections.unmodifiableMap(definitions);
        this.templates = Collections.unmodifiableMap(templates);
    }

    /**
     * 一覧と置き場からテンプレートを準備する。
     *
     * @param catalog テンプレートの一覧
     * @param location 置き場（例 {@code classpath:mail/templates/}。末尾は {@code /}）
     * @param resolver リソースの数え上げと読み込み
     * @return 準備したテンプレート
     * @throws TemplatePreparationException 欠け・壊れ・名前の誤り・一覧に無い templateId があるとき
     */
    public static MailTemplateRegistry prepare(
            List<MailTemplateDefinition> catalog, String location, ResourcePatternResolver resolver) {
        Map<String, MailTemplateDefinition> definitions = new LinkedHashMap<>();
        for (MailTemplateDefinition definition : catalog) {
            if (definitions.putIfAbsent(definition.templateId(), definition) != null) {
                throw new IllegalArgumentException("テンプレートの一覧の templateId が重複しています: " + definition.templateId());
            }
        }
        checkFileNames(definitions, location, resolver);
        Map<String, Template> templates = new LinkedHashMap<>();
        for (String templateId : definitions.keySet()) {
            for (String language : LANGUAGES) {
                templates.put(key(templateId, language), compile(location, resolver, templateId, language));
            }
        }
        return new MailTemplateRegistry(definitions, templates);
    }

    /** 置き場のファイルを数え上げ、名前の形と一覧を照らす（BR2.1・BR2.3）。 */
    private static void checkFileNames(
            Map<String, MailTemplateDefinition> definitions, String location, ResourcePatternResolver resolver) {
        Resource[] resources;
        try {
            resources = resolver.getResources(location + "*.html");
        } catch (IOException e) {
            // 置き場を数え上げられないときは、一覧の templateId のファイルを読む段で MISSING として止まる。
            resources = new Resource[0];
        }
        for (Resource resource : resources) {
            String name = resource.getFilename();
            Matcher matcher = FILE_NAME.matcher(name == null ? "" : name);
            if (!matcher.matches()) {
                throw new TemplatePreparationException(null, null, Reason.INVALID_NAME);
            }
            if (!definitions.containsKey(matcher.group(1))) {
                throw new TemplatePreparationException(matcher.group(1), matcher.group(2), Reason.UNKNOWN_TEMPLATE);
            }
        }
    }

    /** 1つのテンプレートを読んで準備する。部品の例外の文言は持ち出さない。 */
    private static Template compile(
            String location, ResourcePatternResolver resolver, String templateId, String language) {
        Resource resource = resolver.getResource(location + templateId + "_" + language + ".html");
        String source;
        try (InputStream in = resource.getInputStream()) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TemplatePreparationException(templateId, language, Reason.MISSING);
        }
        try {
            return Mustache.compile(source, NO_PARTIALS);
        } catch (MustacheException e) {
            throw new TemplatePreparationException(templateId, language, Reason.PARSE_ERROR);
        }
    }

    private static String key(String templateId, String language) {
        return templateId + "_" + language;
    }

    /**
     * templateId が一覧に有るかを返す。
     *
     * @param templateId テンプレートの識別（null を含む）
     * @return 一覧に有れば true
     */
    public boolean contains(String templateId) {
        return templateId != null && definitions.containsKey(templateId);
    }

    /**
     * templateId の差し込みの名前の集合を返す。
     *
     * @param templateId テンプレートの識別（null を含む）
     * @return 差し込みの名前の集合。一覧に無ければ空
     */
    public Optional<Set<String>> variableNames(String templateId) {
        return contains(templateId) ? Optional.of(definitions.get(templateId).variableNames()) : Optional.empty();
    }

    /**
     * 依頼の言語のテンプレートだけで描く（BR4.1）。差し込む値はエンジンの {@code {{ }}} でエスケープされる（BR2.4）。
     *
     * @param templateId テンプレートの識別
     * @param language 言語
     * @param variables 差し込む値
     * @return 描いた本文。一覧に無い・描く途中で失敗したときは空（TEMPLATE_ERROR。BR4.4）
     */
    public Optional<String> render(String templateId, String language, Map<String, String> variables) {
        Template template = templates.get(key(String.valueOf(templateId), String.valueOf(language)));
        if (template == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(template.render(variables));
        } catch (MustacheException e) {
            return Optional.empty();
        }
    }

    /**
     * 準備したテンプレートの一覧（templateId と language の組の名前）を返す。起動の時の INFO に使う。
     *
     * @return {@code <templateId>_<language>} の名前の並び
     */
    public List<String> preparedTemplateNames() {
        return new ArrayList<>(new TreeSet<>(templates.keySet()));
    }
}
