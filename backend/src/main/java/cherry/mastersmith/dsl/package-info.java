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
 * DSL の定義（U2）。DSL の書式（同梱の JSON Schema と書式の版）、YAML の安全な読み込みと位置の対応表、構文と意味の検証、
 * 変更できないモデル、適用中のモデルの保持と提供口を受け持つ。
 *
 * <p>ほかの機能が使ってよいのは {@code service} のインターフェース（{@code DslReader}・{@code ActiveDslModelHolder}・
 * {@code ActiveDslModelProvider}）と {@code domain} の型だけで、{@code parse}・{@code validate} は外から使わない
 * （{@code DslBoundaryArchitectureTest}）。内部DB・対象DB・DSL の管理の操作は知らない（ADR-001・ADR-009）。
 */
package cherry.mastersmith.dsl;
