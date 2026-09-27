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
package cherry.mastersmith.user.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * パスワードの変更の入力の検証（BR4.1、NFR9.1）。DB を使わない純粋な関数。
 *
 * <ul>
 *   <li>今のパスワード: 空なら {@link FieldErrorReason#REQUIRED}。長さは入力の誤りにしない（72 バイトを超えても照合の結果だけを返す。
 *       {@code nfr-design/security-design.md} 5節）
 *   <li>新しいパスワード: 空なら {@link FieldErrorReason#REQUIRED}、既存の {@link PasswordPolicy} の作成時の規則で 12 コードポイント
 *       未満なら {@link FieldErrorReason#TOO_SHORT}、UTF-8 で 72 バイトを超えれば {@link FieldErrorReason#TOO_LONG}
 *   <li>確かめ: 空なら {@link FieldErrorReason#REQUIRED}、新しいパスワードと文字の並びとして完全に一致しなければ
 *       {@link FieldErrorReason#MISMATCH}（正規化・前後の空白の除去をしない）
 * </ul>
 *
 * <p>誤りは要求の項目の順ですべて集め、1つの項目には最初に当たった理由だけを載せる。
 */
public final class PasswordChangeValidation {

    /** 今のパスワードの項目の名前（契約 C4）。 */
    public static final String CURRENT_PASSWORD = "currentPassword";

    /** 新しいパスワードの項目の名前（契約 C4）。 */
    public static final String NEW_PASSWORD = "newPassword";

    /** 確かめの項目の名前（契約 C4）。 */
    public static final String NEW_PASSWORD_CONFIRMATION = "newPasswordConfirmation";

    private PasswordChangeValidation() {}

    /**
     * 3つを検証する。
     *
     * @param currentPassword 今のパスワード（null でもよい）
     * @param newPassword 新しいパスワード（null でもよい）
     * @param confirmation 確かめの値（null でもよい）
     * @return 項目ごとの誤り（誤りが無ければ空）
     */
    public static List<FieldError> validate(String currentPassword, String newPassword, String confirmation) {
        List<FieldError> errors = new ArrayList<>();
        PreferencesValidation.add(errors, CURRENT_PASSWORD, isEmpty(currentPassword) ? required() : Optional.empty());
        PreferencesValidation.add(errors, NEW_PASSWORD, checkNewPassword(newPassword));
        PreferencesValidation.add(errors, NEW_PASSWORD_CONFIRMATION, checkConfirmation(newPassword, confirmation));
        return List.copyOf(errors);
    }

    /**
     * 新しいパスワードが作成時の規則に合うかを判定する。
     *
     * @param newPassword 新しいパスワード（null でもよい）
     * @return 合わなければ理由（合えば空）
     */
    public static Optional<FieldErrorReason> checkNewPassword(String newPassword) {
        if (isEmpty(newPassword)) {
            return required();
        }
        if (!PasswordPolicy.hasMinimumLength(newPassword)) {
            return Optional.of(FieldErrorReason.TOO_SHORT);
        }
        if (!PasswordPolicy.fitsMaxBytes(newPassword)) {
            return Optional.of(FieldErrorReason.TOO_LONG);
        }
        return Optional.empty();
    }

    private static Optional<FieldErrorReason> checkConfirmation(String newPassword, String confirmation) {
        if (isEmpty(confirmation)) {
            return required();
        }
        return confirmation.equals(newPassword) ? Optional.empty() : Optional.of(FieldErrorReason.MISMATCH);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static Optional<FieldErrorReason> required() {
        return Optional.of(FieldErrorReason.REQUIRED);
    }
}
