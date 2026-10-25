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
 * 業務のメニュー（Intent 261004-role-menu の U5、契約 C9）。作業ロールの実効の主権限で絞った業務のメニューの木を返す読み取りの API を
 * 持つ。テーブルの置き場の問い合わせ（{@code GET /api/me/table-access}）は、画面が呼ばなくなったため Intent
 * 261025-deps-perf-carryover の FR3.3 で削除した。
 *
 * <ul>
 *   <li>{@code navigation.domain}: 応答の木の値、絞る純粋な関数 {@code MenuFilter}、アイコンの照らし合わせ
 *   <li>{@code navigation.service}: 業務処理 {@code NavigationService}、アイコンの一覧のファイルの読み込み、待ち合わせの口
 *   <li>{@code navigation.web}: メニューの API と応答の record
 * </ul>
 *
 * <p>依存してよい先は {@code dsl.service}・{@code dsl.domain}・{@code role.service}・{@code role.domain}・{@code auth.domain}・
 * {@code common} だけ（BR7.1）。どの機能も navigation に依存しない。repository の層と内部DB の表を持たず、読み取りだけ（BR6.2）。
 */
package cherry.mastersmith.navigation;
