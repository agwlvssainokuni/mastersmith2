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
 * 対象DB（U1）。利用者の業務データの DB（MySQL・MariaDB・PostgreSQL）の接続の設定と、読み取り専用の接続でのスキーマの
 * メタデータの読み取りを受け持つ。
 *
 * <p>ほかの機能が使ってよいのは {@code service} の {@code TargetSchemaReader} と {@code domain} の型だけで、{@code config}・
 * {@code repository} は外から使わない（{@code TargetDbBoundaryArchitectureTest}）。DSL のことは知らない。
 */
package cherry.mastersmith.targetdb;
