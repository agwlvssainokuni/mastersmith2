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

import cherry.mastersmith.user.domain.EmailAddress;
import java.util.Objects;

/**
 * 招待先のメールアドレス（正規化済み。BR1.1、NFR2.1）。文字列にすると値を伏せる（メソッドの呼び出しの追跡が DB アクセスの層の引数も
 * 文字列にするため）。問い合わせでは SpEL（{@code :#{#email.value()}}）で取り出す。
 *
 * @param value 前後の空白を除き小文字にそろえた、決まりに合う値
 */
public record InvitationEmail(String value) {

    /** 決まりに合う値であることを確かめる（値は例外のメッセージに載せない）。 */
    public InvitationEmail {
        Objects.requireNonNull(value, "value");
        if (!value.equals(EmailAddress.normalize(value)) || !EmailAddress.isValid(value)) {
            throw new IllegalArgumentException("招待先のメールアドレスが決まりに合いません");
        }
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "InvitationEmail[***]";
    }
}
