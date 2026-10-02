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
 * 利用者の管理（Intent 260930-user-admin の U3、部品 UserAdministration）。管理者だけが使う利用者の一覧と、氏名・言語の変更、
 * 管理の操作の業務の決まりを持つ。自分の表は持たず、{@code user}・{@code auth} の業務処理の口と値の型を使う。
 */
package cherry.mastersmith.useradmin;
