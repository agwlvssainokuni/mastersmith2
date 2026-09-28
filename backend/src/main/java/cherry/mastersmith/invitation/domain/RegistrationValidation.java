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
package cherry.mastersmith.invitation.domain;

import cherry.mastersmith.user.domain.DisplayName;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.PasswordChangeValidation;
import cherry.mastersmith.user.domain.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 登録の完了の入力の検証（BR7.2、NFR9.12、{@code security-design.md} 6節）。DB を使わない純粋な関数で、U2 の決まり（氏名の
 * {@link DisplayName}、表示の設定の値、パスワードの作成時の規則）と同じ関数を使う。
 *
 * <ul>
 *   <li>{@code displayName}: {@link DisplayName#check}（REQUIRED・TOO_LONG・INVALID_CHARACTER）
 *   <li>{@code password}: 作成時の規則（REQUIRED・TOO_SHORT・TOO_LONG）
 *   <li>{@code passwordConfirmation}: 空なら REQUIRED、{@code password} と文字の並びとして完全に一致しなければ MISMATCH（前後の空白を
 *       除かない）
 *   <li>{@code language}・{@code theme}・{@code fontSize}: 完全な一致（REQUIRED・INVALID_VALUE）
 * </ul>
 *
 * <p>誤りは契約 C6 の項目の順ですべて集め、1つの項目には最初に当たった理由だけを載せる。{@code token} は項目にしない（形の誤りは
 * 拒否の経路で扱う。BR3.2）。入れた値は載せない。
 */
public final class RegistrationValidation {

    /** 氏名の項目の名前（契約 C6）。 */
    public static final String DISPLAY_NAME = "displayName";

    /** パスワードの項目の名前（契約 C6）。 */
    public static final String PASSWORD = "password";

    /** 確かめの項目の名前（契約 C6）。 */
    public static final String PASSWORD_CONFIRMATION = "passwordConfirmation";

    /** 言語の項目の名前（契約 C6）。 */
    public static final String LANGUAGE = "language";

    /** テーマの項目の名前（契約 C6）。 */
    public static final String THEME = "theme";

    /** 文字の大きさの項目の名前（契約 C6）。 */
    public static final String FONT_SIZE = "fontSize";

    private RegistrationValidation() {}

    /**
     * 6つを検証する。
     *
     * @param displayName 氏名（null でもよい）
     * @param password パスワード（null でもよい）
     * @param passwordConfirmation 確かめの値（null でもよい）
     * @param language 言語（null でもよい）
     * @param theme テーマ（null でもよい）
     * @param fontSize 文字の大きさ（null でもよい）
     * @return 項目ごとの誤り（誤りが無ければ空）
     */
    public static List<FieldError> validate(
            String displayName,
            String password,
            String passwordConfirmation,
            String language,
            String theme,
            String fontSize) {
        List<FieldError> errors = new ArrayList<>();
        add(errors, DISPLAY_NAME, DisplayName.check(displayName));
        add(errors, PASSWORD, PasswordChangeValidation.checkNewPassword(password));
        add(errors, PASSWORD_CONFIRMATION, checkConfirmation(password, passwordConfirmation));
        add(errors, LANGUAGE, Language.check(language));
        add(errors, THEME, Theme.check(theme));
        add(errors, FONT_SIZE, FontSize.check(fontSize));
        return List.copyOf(errors);
    }

    private static Optional<FieldErrorReason> checkConfirmation(String password, String confirmation) {
        if (confirmation == null || confirmation.isEmpty()) {
            return Optional.of(FieldErrorReason.REQUIRED);
        }
        return confirmation.equals(password) ? Optional.empty() : Optional.of(FieldErrorReason.MISMATCH);
    }

    private static void add(List<FieldError> errors, String field, Optional<FieldErrorReason> reason) {
        reason.ifPresent(value -> errors.add(new FieldError(field, value)));
    }
}
