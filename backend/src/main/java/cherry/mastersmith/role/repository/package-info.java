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
 * ロールと権限の設定の表の読み取り（{@code logical-components.md} の L10）。Spring Data の {@code Repository} を継ぎ、{@code @Query} の
 * 射影だけを置く。書き込みの方法と {@code @Modifying} を置かない（書き込みと排他は {@code role.store} だけ。計画の D-3、
 * {@code RoleBoundaryArchitectureTest}）。
 */
package cherry.mastersmith.role.repository;
