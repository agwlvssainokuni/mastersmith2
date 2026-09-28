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

/** 招待の状態と表の列（大文字の名前 {@code PENDING} など。V8 の {@code ck_invitations_state}）の対応。 */
@Converter
public class InvitationStateConverter implements AttributeConverter<InvitationState, String> {

    @Override
    public String convertToDatabaseColumn(InvitationState attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public InvitationState convertToEntityAttribute(String column) {
        if (column == null) {
            return null;
        }
        for (InvitationState state : InvitationState.values()) {
            if (state.name().equals(column)) {
                return state;
            }
        }
        throw new IllegalStateException("招待の状態が内部DB に想定外の形で保存されています");
    }
}
