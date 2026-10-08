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
 * ロールと権限の設定の表の行の排他と、違反と待ちの上限切れを起こしうる書き込みと flush（{@code logical-components.md} の L2、
 * {@code security-design.md} 4.1）。
 *
 * <p>メソッドの呼び出しの追跡（{@code TraceAspect}）の対象の層（{@code web}・{@code service}・{@code domain}・{@code repository}）の
 * 外の用途名の下位パッケージ。一意・主キー・外部キーの違反の文と、行の排他の上限切れの例外の連なりの奥には、行の値（ロールの名前・ID・
 * 対象の名前）が入る。例外をこのパッケージの中で受けて結果の型（{@link cherry.mastersmith.role.store.RoleStoreOutcome}）に変え、
 * 例外の文を追跡・応答・ログに出さない（BR12.3、NFR1.8）。group の {@code group.store} と同じ形。
 *
 * <p>呼び出し元（{@code role.service}）のトランザクションの中で使い、自分ではトランザクションを始めない。巻き戻しの印は呼び出し元の
 * {@code RoleStoreTransactions} が付ける。使ってよいのは {@code role.service} だけ（{@code RoleBoundaryArchitectureTest}）。
 */
package cherry.mastersmith.role.store;
