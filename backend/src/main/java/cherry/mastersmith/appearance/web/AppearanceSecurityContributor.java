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
package cherry.mastersmith.appearance.web;

import cherry.mastersmith.common.security.SecurityRuleContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * U8 がフィルターの連鎖に足す公開の決まり（BR3.2・BR3.3、NFR4.1〜NFR4.4・NFR4.7）。
 *
 * <p>メソッド GET・道 {@code /api/appearance} だけを認証なしにする決まりを1つ足す。ほかのメソッドと道は {@code /api/**} の既定
 * （ログインが必要）のままで、トークンの検証・入口の処理・ヘッダー・セッション・CSRF の設定は足さない・変えない（既存のまま）。
 *
 * <p>order は 410。割り当ては機能の名前で 100 台ずつで、{@code appearance} は 400 台（400〜499）を使う。本番の決まりは x10、
 * x00・x50 はテストの決まりが使う。
 */
@Component
public class AppearanceSecurityContributor implements SecurityRuleContributor {

    /** この決まりの順番（appearance は 400 台。x00・x50 はテストの決まりが使う）。 */
    public static final int ORDER = 410;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void contribute(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, AppearanceController.PATH)
                .permitAll());
    }
}
