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
package cherry.mastersmith.businessdata.domain;

/**
 * 要求が指すテーブルと、画面が持っている定義の識別（定義・一覧と検索・詳細の本文に共通の部分。BR1.3・BR9.1）。名前はまだ DSL と
 * 照らしていない。
 *
 * @param schema スキーマ名（照らす前）
 * @param table テーブル名（照らす前）
 * @param definitionId 画面が持っている定義の識別（無ければ null）
 */
public record TableQuery(String schema, String table, RequestedDefinitionId definitionId) {}
