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
 * グループの管理（Intent 261004-role-menu の U3、部品 GroupManagement）。グループとメンバーの管理の API と、U4 role への読み取りと排他の口
 * （契約 C4）を持つ。
 *
 * <p>依存してよい先は {@code user}（業務処理の口と値の型）と {@code common} だけ。{@code group} に依存してよいのは {@code role}
 * （口だけ）と {@code audit}（出来事の型だけ）だけで、{@code group} は {@code role}・{@code audit}・{@code useradmin} に依存しない
 * （{@code GroupBoundaryArchitectureTest}）。グループの表とメンバーの表の書き込みと行の排他は、メソッドの呼び出しの追跡の対象の外の
 * 用途名の下位パッケージ {@code group.store} だけが行う。
 */
package cherry.mastersmith.group;
