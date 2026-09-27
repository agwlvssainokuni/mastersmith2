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
package cherry.mastersmith.common.security;

import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * 差し込み口「追加のアクセスの決まり」。各機能（auth・access・invitation・appearance など）が Bean として置き、U1 のフィルターの
 * 連鎖に決まりを足す（0個以上）。
 *
 * <p>呼ぶ順番: U1 の公開の決まり → この型の Bean を {@link #getOrder()} の小さい順 → {@link ApiDefaultAccess} による
 * {@code /api/**} の既定 → 画面の配信の許可。
 *
 * <p>order の割り当て: 単位の番号ではなく機能の名前で 100 台ずつ割り当てる。{@code auth} は 100 台（100〜199、本番 110）、
 * {@code access} は 200 台（200〜299、本番 210）、{@code invitation} は 300 台（300〜399）、{@code appearance} は 400 台
 * （400〜499、本番 410）を使う。本番の決まりは x10 を使い、x00・x50 はテストの決まりが使う（100・150・200・250）。同じ値が2つ
 * あれば起動を失敗させる。
 *
 * <p>足してよいもの: アクセスの決まり、トークンの検証（OAuth2 Resource Server）、認証の入口の処理と拒否の処理、要求の検査の拒否の
 * 処理。ヘッダー・セッション・CSRF の設定は変えない（U1 だけが決める）。
 */
public interface SecurityRuleContributor extends Ordered {

    /**
     * フィルターの連鎖に決まりを足す。
     *
     * @param http フィルターの連鎖の設定
     * @throws Exception 設定に失敗したとき
     */
    void contribute(HttpSecurity http) throws Exception;
}
