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
package cherry.mastersmith.user.service;

import cherry.mastersmith.user.domain.Password;
import java.util.Objects;

/**
 * パスワードの変更の入力（検証する前の値）。3つとも文字列化で値を伏せる {@link Password} で持つ（NFR2.2、BR8.4）。
 *
 * @param currentPassword 今のパスワード（値が無ければ空の文字列）
 * @param newPassword 新しいパスワード（値が無ければ空の文字列）
 * @param newPasswordConfirmation 確かめの値（値が無ければ空の文字列）
 */
public record PasswordChangeCommand(Password currentPassword, Password newPassword, Password newPasswordConfirmation) {

    /** 3つが有ることを確かめる（値が無いことは空の文字列で表す）。 */
    public PasswordChangeCommand {
        Objects.requireNonNull(currentPassword, "currentPassword");
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(newPasswordConfirmation, "newPasswordConfirmation");
    }

    /**
     * 要求の文字列から作る。値が無い（null）項目は空の文字列にする（どちらも入力の誤りの {@code REQUIRED} になる）。
     *
     * @param currentPassword 今のパスワード（null でもよい）
     * @param newPassword 新しいパスワード（null でもよい）
     * @param newPasswordConfirmation 確かめの値（null でもよい）
     * @return 入力
     */
    public static PasswordChangeCommand of(String currentPassword, String newPassword, String newPasswordConfirmation) {
        return new PasswordChangeCommand(wrap(currentPassword), wrap(newPassword), wrap(newPasswordConfirmation));
    }

    private static Password wrap(String value) {
        return new Password(value == null ? "" : value);
    }
}
