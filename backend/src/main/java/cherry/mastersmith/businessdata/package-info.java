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
 * 業務データ（Intent 261016-business-data-views の U1・U2）。適用中の DSL と作業ロールの権限に従って、対象DB の業務データの定義・
 * 一覧と検索・詳細を読む（U1）。
 *
 * <p>権限は {@code role} の権限の写し（{@code PermissionSnapshot}）1つで判定し、同じ写しの DSL で名前を照らしてから SQL に使う。
 * 行の SQL を組み立てて実行するのは {@code repository} の {@code RowSqlExecutor} だけで、対象DB の接続は {@code targetdb.service}
 * の読み取りの口（{@code TargetReadAccess}）から貸す形で受け取る。依存してよい相手と SQL を実行してよい場所は
 * {@code BusinessDataBoundaryArchitectureTest} で固める。
 */
package cherry.mastersmith.businessdata;
