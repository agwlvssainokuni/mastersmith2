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
 * ロールと権限の業務処理（{@code logical-components.md} の L3・L8・L12・L15）。トランザクションの境界はこの層の
 * {@code TransactionTemplate} だけで、store を呼ぶ1つ目のトランザクションはすべて {@code RoleStoreTransactions} を通す。group の問う口は
 * B3 の仮の実装のまま（B5 で本物に置き換える）。
 */
package cherry.mastersmith.role.service;
