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

import java.util.Objects;

/**
 * 招待の URL（BR3.4・BR3.5、NFR1.4）。使えるベース URL だけから「ベース URL ＋ {@code /register#token=} ＋ トークン」で組み立て、要求の
 * Host ヘッダーから組み立てない。トークンは URL のフラグメントにだけ載る。
 *
 * <p>保存・応答・ログに出さず、招待メールの依頼（差し込み {@code registrationUrl}）にだけ渡す。文字列にすると値を伏せる。
 *
 * @param value URL の文字列
 */
public record RegistrationUrl(String value) {

    /** トークンの前に置く、登録の画面の道とフラグメントの名前。 */
    public static final String PATH_AND_FRAGMENT = "/register#token=";

    /** 値が空でないことを確かめる。 */
    public RegistrationUrl {
        Objects.requireNonNull(value, "value");
    }

    /**
     * 招待の URL を組み立てる。
     *
     * @param baseUrl {@link BaseUrlRule} で確かめた使える値（末尾の {@code /} を除いた値）
     * @param token 招待のトークン
     * @return 招待の URL
     */
    public static RegistrationUrl of(String baseUrl, InvitationToken token) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(token, "token");
        return new RegistrationUrl(baseUrl + PATH_AND_FRAGMENT + token.value());
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "RegistrationUrl[****]";
    }
}
