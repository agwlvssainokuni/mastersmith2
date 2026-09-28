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
 * Invitation の画面入出力（招待の管理の API（契約 C5、管理者だけ）と、登録の完了の API（契約 C6、ログインなし））。
 *
 * <p>要求の項目はすべて文字列で受け、業務処理の結果の型を業務エラーに変える。応答は共通の変換（{@code @RestControllerAdvice} の
 * 1か所）で作る。公開の2つの POST は差し込み口（order 310）で認証なしにする。
 */
package cherry.mastersmith.invitation.web;
