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
 * ロールと権限（Intent 261004-role-menu の U4、部品 RoleManagement）。B3 の時点では、group が定義する問う口の仮の実装だけを持つ
 * （計画の 11節 Q1: A、BR6.3）。ロールの表・割り当て・作業ロール・権限の解決は B4・B5 で足す。
 *
 * <p>アプリの中で依存してよいのは {@code group.service}（口だけ）と {@code common} だけ（{@code RoleBoundaryArchitectureTest}。B4 以降は
 * role の Bolt が規則を足す）。
 */
package cherry.mastersmith.role;
