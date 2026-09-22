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
 * Authentication（ログイン、アクセストークンとリフレッシュトークン、アカウントロック、ログアウト）。
 *
 * <p>利用者とパスワードの照合は {@code user.service} の公開の操作だけを使い、{@code user} のエンティティ・DB アクセスを参照しない
 * （ADR-001）。監査（{@code audit}）は参照せず、出来事で知らせる（ADR-004）。
 */
package cherry.mastersmith.auth;
