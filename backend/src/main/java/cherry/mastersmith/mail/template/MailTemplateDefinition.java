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

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * テンプレートの一覧の1行（entities.md の MailTemplateDefinition、BR2.2）。templateId と、テンプレートが受け取る差し込みの名前の
 * 集合を持つ。
 *
 * @param templateId テンプレートの識別（英小文字で始まり英小文字・数字・ハイフンだけ。下線はファイル名の区切りに使うため不可）
 * @param variableNames 差し込みの名前の集合（英字で始まり英数字だけ。0件もありうる）
 */
public record MailTemplateDefinition(String templateId, Set<String> variableNames) {

    /** templateId の形（BR2.1）。 */
    public static final Pattern TEMPLATE_ID = Pattern.compile("[a-z][a-z0-9-]*");

    /** 差し込みの名前の形。 */
    public static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    /** 形を確かめ、名前の集合を変更できない写しにする。 */
    public MailTemplateDefinition {
        Objects.requireNonNull(templateId, "templateId は必須です");
        if (!TEMPLATE_ID.matcher(templateId).matches()) {
            throw new IllegalArgumentException("templateId の形が違います: " + templateId);
        }
        variableNames = Set.copyOf(Objects.requireNonNull(variableNames, "variableNames は必須です"));
        for (String name : variableNames) {
            if (!VARIABLE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("差し込みの名前の形が違います: " + name);
            }
        }
    }
}
