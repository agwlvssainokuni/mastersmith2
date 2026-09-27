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
 * UserAccount（利用者と照合、氏名と表示の設定、パスワードの変更）。パスワードのハッシュはこのパッケージの外へ出さない（ADR-001）。
 *
 * <p>ほかの機能は {@code user.service} の公開の操作（照合の結果と利用者の要約、利用者の作成）だけを使う。このパッケージは
 * {@code auth}・{@code audit} を参照しない。ログインした利用者の自分の設定の API は画面入出力の層 {@code user.web} に置く。
 */
package cherry.mastersmith.user;
