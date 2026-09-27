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
package cherry.mastersmith.user.web;

/**
 * パスワードの変更の要求（{@code POST /api/me/password}、契約 C4 の PasswordChangeRequest）。
 *
 * <p>項目はすべて文字列で受け、Bean Validation の注釈を付けない（まとめて検証するため）。業務処理へは文字列化で伏せる
 * {@code Password} の型で渡す。文字列化では3つのパスワードを伏せる（既存の {@code LoginRequest} と同じ扱い）。
 *
 * @param currentPassword 今のパスワード
 * @param newPassword 新しいパスワード
 * @param newPasswordConfirmation 確かめの値
 */
public record PasswordChangeRequest(String currentPassword, String newPassword, String newPasswordConfirmation) {

    /** 3つのパスワードを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=***, newPassword=***, newPasswordConfirmation=***]";
    }
}
