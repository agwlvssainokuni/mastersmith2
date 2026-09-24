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
 * DSL の管理の画面入出力（U4、契約 C6 の {@code /api/admin/dsl/**}）。HTTP の受け渡し（入力の形、DTO への変換、状態コード）だけを
 * 行い、DB アクセスの層を呼ばない。想定内の失敗は業務処理の層が業務の例外にし、共通の変換（{@code GlobalExceptionHandler}）が
 * 応答にする。
 *
 * <p>重い道（生成・投入・履歴からの戻し・プレビューの表示）は、本文を読む前に同時に1つの許可を取る
 * （{@link cherry.mastersmith.dslmanage.web.DslHeavyOperationGate}）。
 */
package cherry.mastersmith.dslmanage.web;
