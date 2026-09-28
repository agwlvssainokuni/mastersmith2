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
package cherry.mastersmith.invitation.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 送信の結果と表の列（大文字の名前 {@code PENDING}・{@code SENT}・{@code FAILED}）の対応。 */
@Converter
public class SendResultConverter implements AttributeConverter<SendResult, String> {

    @Override
    public String convertToDatabaseColumn(SendResult attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public SendResult convertToEntityAttribute(String column) {
        if (column == null) {
            return null;
        }
        for (SendResult result : SendResult.values()) {
            if (result.name().equals(column)) {
                return result;
            }
        }
        throw new IllegalStateException("送信の結果が内部DB に想定外の形で保存されています");
    }
}
