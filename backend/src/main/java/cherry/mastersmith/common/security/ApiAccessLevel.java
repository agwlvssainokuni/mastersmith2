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
package cherry.mastersmith.common.security;

/**
 * API の分類の値（契約 C1、ADR-008、機能設計 ENT-001）。コントローラーの口がどの範囲の利用者に開かれているかを表す。
 *
 * <p>値は3つだけ。値を足すのは互換の変更として後の Intent で扱い、今ある値の名前と意味は変えない。分類は口の隣の印
 * {@link ApiAccess} で示し、本番の判定は今までどおり Spring Security の決まりが行う（この値は判定に使わない）。
 */
public enum ApiAccessLevel {

    /** ログインしなくても届く（例: ログイン・登録の完了・見た目の設定・問題の種類の説明・{@code /error}）。 */
    PUBLIC,

    /** ログインだけが要る（{@code /api/**} の既定。例: {@code /api/me/**}）。道は {@code /api/} の下で、管理者の道の外にある。 */
    AUTHENTICATED,

    /** 管理者の印が要る（{@code /api/admin} そのものと {@code /api/admin/} の下）。 */
    ADMIN
}
