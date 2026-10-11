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
 * 業務のメニュー（Intent 261004-role-menu の U5、契約 C9）。作業ロールの実効の主権限で絞った業務のメニューの木と、テーブルの置き場の
 * 問い合わせの2本の読み取りの API を持つ。
 *
 * <ul>
 *   <li>{@code navigation.domain}: 応答の木の値、絞る純粋な関数 {@code MenuFilter}、アイコンの照らし合わせ、結果の型
 *   <li>{@code navigation.service}: 業務処理 {@code NavigationService}、アイコンの一覧のファイルの読み込み、待ち合わせの口
 *   <li>{@code navigation.web}: 2本の API と応答の record
 * </ul>
 *
 * <p>依存してよい先は {@code dsl.service}・{@code dsl.domain}・{@code role.service}・{@code role.domain}・{@code auth.domain}・
 * {@code common} だけ（BR7.1）。{@code ACCESS_DENIED} は {@code access} に依存せず、共通の問題の種類の一覧から code で引く（計画の
 * 13節 Q2: A）。どの機能も navigation に依存しない。repository の層と内部DB の表を持たず、読み取りだけ（BR6.2）。
 */
package cherry.mastersmith.navigation;
