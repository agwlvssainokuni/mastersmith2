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
 * グループの表とメンバーの表の行の排他と、違反と待ちの上限切れを起こしうる書き込みと flush（NFR 設計の Q3: A、
 * {@code logical-components.md} の L2）。
 *
 * <p>メソッドの呼び出しの追跡（{@code TraceAspect}）の対象の層（{@code web}・{@code service}・{@code domain}・{@code repository}）の
 * 外の用途名の下位パッケージ。一意・主キー・外部キーの違反の文と、行の排他の上限切れの例外の連なりの奥には、行の値（名前・ID）が入る
 * （捨ての試しの T6）。例外をこのパッケージの中で受けて結果の型（{@link cherry.mastersmith.group.store.StoreOutcome}）に変え、例外の文を
 * 追跡・応答・ログに出さない（BR9.3、NFR1.8）。既存の {@code invitation.lock} と同じ形。
 *
 * <p>呼び出し元（{@code group.service}）のトランザクションの中で使い、自分ではトランザクションを始めない。巻き戻しの印は呼び出し元の
 * {@code GroupStoreTransactions} が付ける。使ってよいのは {@code group.service} だけ（{@code GroupBoundaryArchitectureTest}）。
 */
package cherry.mastersmith.group.store;
