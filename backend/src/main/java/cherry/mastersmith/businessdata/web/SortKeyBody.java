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
package cherry.mastersmith.businessdata.web;

/**
 * 並べ替えのカラムと向き（契約 C2 の {@code SortKey}）。要求と応答の両方に使う。
 *
 * @param column カラムの名前
 * @param direction 向き（{@code ASC}・{@code DESC}）
 */
public record SortKeyBody(String column, String direction) {}
