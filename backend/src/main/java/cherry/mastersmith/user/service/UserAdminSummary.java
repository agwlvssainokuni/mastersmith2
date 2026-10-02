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
package cherry.mastersmith.user.service;

import cherry.mastersmith.user.domain.Language;
import java.time.Instant;
import java.util.Objects;

/**
 * 利用者の管理に渡す利用者の要約（Intent 260930-user-admin の U3、契約 C8 の UserAdminSummary、FS の D9）。パスワードのハッシュ値は
 * 持たない。文字列にするとメールアドレスと氏名を伏せる（メソッドの呼び出しの追跡が業務処理の戻り値を文字列にするため。BR7.4）。
 *
 * @param userId 利用者 ID
 * @param email メールアドレス（小文字にそろえた値）
 * @param displayName 氏名
 * @param language 言語
 * @param admin 管理者の印
 * @param suspended 利用停止中か
 * @param registeredAt 登録した日時
 */
public record UserAdminSummary(
        long userId,
        String email,
        String displayName,
        Language language,
        boolean admin,
        boolean suspended,
        Instant registeredAt) {

    /** 値が null でないことを確かめる。 */
    public UserAdminSummary {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }

    /** メールアドレスと氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "UserAdminSummary[userId=" + userId + ", email=***, displayName=***, language=" + language.value()
                + ", admin=" + admin + ", suspended=" + suspended + ", registeredAt=" + registeredAt + "]";
    }
}
