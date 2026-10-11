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
/**
 * ロールと権限（Intent 261004-role-menu の U4、部品 RoleManagement）。ロールの管理と権限の設定（B4）、利用者・グループへの割り当て・
 * 作業ロール・実効の権限の解決の口 {@code role.service.EffectivePermissionResolver}（B5、契約 C5）、権限の YAML の書き出し・確かめ・
 * 適用（B6）を持つ。group が定義する問う口（グループの削除の確かめ）も実装する。
 *
 * <p>アプリの中で依存してよいのは {@code group}・{@code user}・{@code dsl} の業務処理の口とドメインの型、{@code common} だけ。
 * {@code role} に依存してよいのは {@code audit}（{@code role.domain} の出来事の型だけ）と {@code navigation}（{@code role.service}
 * の口だけ）だけ（{@code RoleBoundaryArchitectureTest}）。ロールの表・設定・割り当ての書き込みと行の排他は、メソッドの呼び出しの追跡の
 * 対象の外の用途名の下位パッケージ {@code role.store} だけが行い、YAML の受け渡しの純粋な部品は {@code role.transfer} に置く。
 */
package cherry.mastersmith.role;
