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
package cherry.mastersmith.auth.web;

import cherry.mastersmith.user.service.UserSummary;

/**
 * ログイン中の利用者（CurrentUserView）。{@code admin} は画面の表示の切り替えにだけ使う。
 *
 * <p>氏名と表示の設定の4つを載せる（Intent 260925-user-management の契約 C3、BR6.1）。値は応答を作る時点の内部DB の値で、テーマ
 * {@code system} は解決せずにそのまま返す。
 *
 * @param email メールアドレス
 * @param admin 管理者か
 * @param displayName 氏名
 * @param language 言語（{@code ja}・{@code en}）
 * @param theme テーマ（{@code light}・{@code dark}・{@code system}）
 * @param fontSize 文字の大きさ（{@code sm}・{@code md}・{@code lg}）
 */
public record CurrentUserResponse(
        String email, boolean admin, String displayName, String language, String theme, String fontSize) {

    /**
     * 利用者の要約から作る。
     *
     * @param user 利用者の要約
     * @return ログイン中の利用者
     */
    public static CurrentUserResponse from(UserSummary user) {
        return new CurrentUserResponse(
                user.email(), user.admin(), user.displayName(), user.language(), user.theme(), user.fontSize());
    }
}
