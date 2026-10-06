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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * コントローラーの口に付ける API の分類の印（契約 C1、ADR-008、機能設計 ENT-002）。
 *
 * <p>分類の意味は {@link ApiAccessLevel} のとおり。印は読み手と検査のためのもので、本番の判定は今までどおり Spring Security の
 * 決まり（{@code SecurityConfig}・各機能の {@link SecurityRuleContributor}・{@link ApiDefaultAccess}）が行う。
 *
 * <p>決まり:
 *
 * <ul>
 *   <li>1つの口に効く印は1つだけ。コントローラーのクラスか、RequestMapping を持つ方法のどちらか一方に付ける（両方には付けない）。
 *       クラスの中の口がすべて同じ分類ならクラスに、違う分類を混ぜるなら方法に付ける。
 *   <li>{@link ApiAccessLevel#ADMIN} の口の道は管理者の道（{@code /api/admin} そのものと {@code /api/admin/} の下）にあり、
 *       管理者の道の口は必ず {@link ApiAccessLevel#ADMIN} にする。
 *   <li>{@link ApiAccessLevel#AUTHENTICATED} の口の道は {@code /api/} の下で、管理者の道の外にする。
 *   <li>新しい口を足すときは、同じ変更でこの印を付ける。{@link ApiAccessLevel#PUBLIC} を足す・外すときは、テストの側の PUBLIC の
 *       口の一覧（{@code common/testsupport/PublicApiInventory}）の行と理由も同じ変更で直す。
 * </ul>
 *
 * <p>検査の場所（どちらもテストのソース）: 静的な構造の検査 {@code ApiAccessArchitectureTest}（印の書き忘れ・二重、道との食い違い）と、
 * 実行時の検査 {@code ApiAccessConsistencyIT}（印と本番の安全の決まりの判定の一致、PUBLIC の口の一覧）。
 *
 * <p>この注釈は {@code common.security} に置き、どの機能のパッケージにも依存しない。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiAccess {

    /**
     * 分類の値。
     *
     * @return 分類の値
     */
    ApiAccessLevel value();
}
