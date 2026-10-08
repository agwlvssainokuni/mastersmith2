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
package cherry.mastersmith.role.web;

/**
 * ロールの作成・名前の変更の要求の本文（契約 C7、BR2.3）。{@code name} だけを受け、ID・作成者・時刻・組み込みの印などのほかの項目は
 * 型に無いため読まれない（一括代入の防止。NFR1.4）。ロールの名前は個人に関する値として扱わない。
 *
 * @param name 名前（正規化の前の値。無ければ null）
 */
public record RoleNameRequest(String name) {}
