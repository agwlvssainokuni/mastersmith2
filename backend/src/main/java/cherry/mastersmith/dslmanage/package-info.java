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
 * DSL の管理（U3・U4）。既定の DSL の生成（{@code generate}。U3）と、DSL の投入・プレビュー・適用・履歴の管理（U4）を受け持つ。
 *
 * <p>DSL の書式と検証は {@code cherry.mastersmith.dsl}（U2）、対象DB の読み取りは {@code cherry.mastersmith.targetdb}（U1）に
 * 任せ、どちらも {@code service} のインターフェースと {@code domain} の型だけを使う（ADR-001）。
 */
package cherry.mastersmith.dslmanage;
