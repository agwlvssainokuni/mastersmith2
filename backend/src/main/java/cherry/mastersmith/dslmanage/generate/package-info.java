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
 * 既定の DSL の生成（U3、契約 C5 の {@code DefaultDslGenerator}）。対象DB のスキーマの写し（U1）から、U2 の書式の YAML の本文を
 * 作り、U2 で検証して返す。保存と監査はしない（U4）。
 *
 * <p>U1 の {@code targetdb.service}・{@code targetdb.domain} と、U2 の {@code dsl.service}・{@code dsl.domain} だけを使う
 * （{@code DslManageGenerateBoundaryArchitectureTest}）。生成した DSL・結果・ログに、接続先・ユーザー名・パスワードを含めない
 * （NFR4.9、BR5.3）。
 */
package cherry.mastersmith.dslmanage.generate;
