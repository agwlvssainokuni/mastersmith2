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
package cherry.mastersmith.user.repository;

import cherry.mastersmith.user.domain.Language;
import java.time.Instant;
import java.util.Objects;

/**
 * 利用者の一覧と要約の投影（Intent 260930-user-admin の U3、BR1.6・BR1.8。計画の D-1・Q-A）。
 *
 * <p>一覧の問い合わせはこの投影で読み、パスワードのハッシュの列を読まない。{@code user.service} が利用者の要約に写す（問い合わせの中で
 * {@code user.service} の型を作ると、{@code user.repository} が {@code user.service} に依存して循環するため）。文字列にすると
 * メールアドレスと氏名を伏せる（メソッドの呼び出しの追跡が DB アクセスの層の戻り値を文字列にするため）。
 *
 * @param userId 利用者 ID
 * @param email メールアドレス（小文字にそろえた値）
 * @param displayName 氏名
 * @param language 言語
 * @param admin 管理者の印
 * @param suspended 利用停止中か
 * @param registeredAt 登録した日時（{@code users.created_at}）
 */
public record UserAdminRow(
        long userId,
        String email,
        String displayName,
        Language language,
        boolean admin,
        boolean suspended,
        Instant registeredAt) {

    /** 値が null でないことを確かめる。 */
    public UserAdminRow {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }

    /** メールアドレスと氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "UserAdminRow[userId=" + userId + ", email=***, displayName=***, language=" + language.value()
                + ", admin=" + admin + ", suspended=" + suspended + ", registeredAt=" + registeredAt + "]";
    }
}
