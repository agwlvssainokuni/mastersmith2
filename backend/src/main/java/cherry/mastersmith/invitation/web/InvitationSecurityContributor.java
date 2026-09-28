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
package cherry.mastersmith.invitation.web;

import cherry.mastersmith.common.security.SecurityRuleContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * Invitation がフィルターの連鎖に足す公開の決まり（BR9.2、NFR4.2・NFR4.3、ADR-011、{@code security-design.md} 3節）。
 *
 * <p>メソッド POST・道 {@code /api/registration/verify} と {@code /api/registration/complete} の2つだけを認証なしにする。
 * {@code /api/registration/} のほかの道と、2つの道のほかのメソッドは {@code /api/**} の既定（ログインが必要）のまま。Origin の確かめ・
 * CSRF・クッキー・トークンの読み取りの設定は足さない・変えない（公開の道に届いた壊れたアクセストークンは既存のとおり 401。Q3 A）。
 *
 * <p>order は 310。割り当ては機能の名前で 100 台ずつで、{@code invitation} は 300 台（300〜399）を使う。本番の決まりは x10、x00・x50 は
 * テストの決まりが使う。
 */
@Component
public class InvitationSecurityContributor implements SecurityRuleContributor {

    /** この決まりの順番（invitation は 300 台。x00・x50 はテストの決まりが使う）。 */
    public static final int ORDER = 310;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void contribute(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        HttpMethod.POST, RegistrationController.VERIFY_PATH, RegistrationController.COMPLETE_PATH)
                .permitAll());
    }
}
