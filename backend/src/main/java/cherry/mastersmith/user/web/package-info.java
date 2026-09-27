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
 * UserAccount の画面入出力（ログインした利用者の自分の設定の API、契約 C4）。
 *
 * <p>{@code GET}・{@code PUT /api/me/preferences}（氏名と表示の設定）と {@code POST /api/me/password}（パスワードの変更）を
 * 受け持つ。既存の {@code /api/} の既定のログイン必須に乗り、対象はアクセストークンの本人だけで決める（URL・本文で利用者の
 * 識別を受け取らない）。本人の利用者 ID は Spring Security の標準の {@code Authentication#getName()} から読み、{@code auth} には
 * 依存しない。業務処理の結果の型を、共通のエラー応答の仕組み（業務エラー）で Problem Details に変える。
 */
package cherry.mastersmith.user.web;
