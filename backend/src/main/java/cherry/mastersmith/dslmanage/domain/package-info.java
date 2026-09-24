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
 * DSL の管理のドメイン（U4）。保存するもの（プレビューと適用の履歴のエンティティ）、プレビューの表示の値（要約・違い・照合の警告）、
 * 監査への出来事（契約 C7）、問題の種類（契約 C6 の code）。
 *
 * <p>どの値も、対象DB の接続先・ユーザー名・パスワードを持たない。エンティティは API の応答として直接返さない。
 */
package cherry.mastersmith.dslmanage.domain;
