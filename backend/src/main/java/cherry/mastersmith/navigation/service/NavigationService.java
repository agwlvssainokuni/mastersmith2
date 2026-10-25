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
package cherry.mastersmith.navigation.service;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.navigation.domain.MenuFilter;
import cherry.mastersmith.navigation.domain.MenuFilterInput;
import cherry.mastersmith.navigation.domain.NavIconPolicy;
import cherry.mastersmith.navigation.domain.NavigationMenu;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
import org.springframework.stereotype.Service;

/**
 * 業務のメニューの業務処理（{@code logical-components.md} の L2、{@code functional-spec.md} の 2.1・2.2、BR1.2〜BR1.5・BR5.2〜BR5.4）。
 *
 * <ul>
 *   <li>メニュー: 適用中の DSL を {@code current()} で1回読み、DSL が無い・menus が空なら NOT_CONFIGURED。そうでなければ
 *       {@code snapshotFor} を1回呼び、写しのテーブルの主権限で {@link MenuFilter} に絞らせる。写しと DSL の識別が食い違っても読み直さない
 * </ul>
 *
 * <p>トランザクションを開かず、写しを要求をまたいで持たない（キャッシュを置かない）。解決の口の DB の誤りは例外のまま投げる（想定外の
 * 失敗）。受け渡す値は利用者 ID・名前・表示名・権限の値だけ（BR7.4）。
 */
@Service
public class NavigationService {

    private final ActiveDslModelProvider dslProvider;

    private final EffectivePermissionResolver resolver;

    private final NavIconPolicy icons;

    private final NavigationBarrier barrier;

    /**
     * 作る。
     *
     * @param dslProvider 適用中の DSL の提供口
     * @param resolver 実効の権限の解決の口
     * @param iconList アイコンの許した名前の一覧
     * @param barrier 待ち合わせの口（本番は何もしない）
     */
    public NavigationService(
            ActiveDslModelProvider dslProvider,
            EffectivePermissionResolver resolver,
            AllowedNavIconList iconList,
            NavigationBarrier barrier) {
        this.dslProvider = dslProvider;
        this.resolver = resolver;
        this.icons = iconList.policy();
        this.barrier = barrier;
    }

    /**
     * 利用者の作業ロールで絞った業務のメニューを返す。
     *
     * @param userId 利用者 ID（要求の文脈の主体）
     * @return 絞った木と空の理由
     */
    public NavigationMenu menu(long userId) {
        if (!(dslProvider.current() instanceof ActiveDsl.Present present)
                || present.model().menus().isEmpty()) {
            return NavigationMenu.notConfigured();
        }
        barrier.afterDslRead();
        PermissionSnapshot snapshot = resolver.snapshotFor(userId);
        MenuFilterInput input =
                new MenuFilterInput((schema, table) -> snapshot.main(schema, table, null), icons::iconOf);
        return NavigationMenu.filtered(MenuFilter.filter(present.model().menus(), input));
    }
}
