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
package cherry.mastersmith.user.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** テーマと表の列（小文字の文字列 {@code light}・{@code dark}・{@code system}）の対応（V7）。 */
@Converter
public class ThemeConverter implements AttributeConverter<Theme, String> {

    @Override
    public String convertToDatabaseColumn(Theme attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Theme convertToEntityAttribute(String column) {
        return PreferenceValueRules.fromColumn(Theme.values(), column);
    }
}
