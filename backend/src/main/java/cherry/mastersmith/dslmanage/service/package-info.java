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
 * DSL の管理の業務処理（U4）。生成・投入・適用・破棄・起動時の読み込み、要約・違い・照合、監査の出来事、指標とログ。
 *
 * <p>トランザクションの境界はこの層だけに置く。U1・U2・U3 の結果の型（想定内の失敗）は、ここで業務の例外に変える（BR8.1）。
 * 適用中のモデルの差し替えと監査の出来事は、確定の後にだけ行う（BR4.6）。
 */
package cherry.mastersmith.dslmanage.service;
