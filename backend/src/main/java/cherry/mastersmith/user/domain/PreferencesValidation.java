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
 * プリファレンスの保存の入力の検証（BR3.2）。DB を使わない純粋な関数。
 *
 * <p>4つすべてを検証し、誤りのある項目を要求の項目の順（displayName・language・theme・fontSize）ですべて集める。1つの項目には
 * 最初に当たった理由だけを載せ、入れた値は載せない（{@code nfr-design/security-design.md} 3節）。
 */
public final class PreferencesValidation {

    /** 氏名の項目の名前（契約 C4）。 */
    public static final String DISPLAY_NAME = "displayName";

    /** 言語の項目の名前（契約 C4）。 */
    public static final String LANGUAGE = "language";

    /** テーマの項目の名前（契約 C4）。 */
    public static final String THEME = "theme";

    /** 文字の大きさの項目の名前（契約 C4）。 */
    public static final String FONT_SIZE = "fontSize";

    private PreferencesValidation() {}

    /**
     * 4つを検証する。
     *
     * @param displayName 氏名（前後の空白を除く前の値。null でもよい）
     * @param language 言語（null でもよい）
     * @param theme テーマ（null でもよい）
     * @param fontSize 文字の大きさ（null でもよい）
     * @return 項目ごとの誤り（誤りが無ければ空）
     */
    public static List<FieldError> validate(String displayName, String language, String theme, String fontSize) {
        List<FieldError> errors = new ArrayList<>();
        add(errors, DISPLAY_NAME, DisplayName.check(displayName));
        add(errors, LANGUAGE, Language.check(language));
        add(errors, THEME, Theme.check(theme));
        add(errors, FONT_SIZE, FontSize.check(fontSize));
        return List.copyOf(errors);
    }

    /**
     * 検証を通った4つから組を作る（氏名は前後の空白を除いた値にする）。
     *
     * @param displayName 氏名
     * @param language 言語
     * @param theme テーマ
     * @param fontSize 文字の大きさ
     * @return 4つの組
     * @throws IllegalArgumentException 決まりに合わない値があるとき（先に {@link #validate} で確かめる）
     */
    public static Preferences toPreferences(String displayName, String language, String theme, String fontSize) {
        List<FieldError> errors = validate(displayName, language, theme, fontSize);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("プリファレンスが決まりに合いません: " + errors);
        }
        return new Preferences(
                DisplayName.strip(displayName),
                Language.fromValue(language).orElseThrow(),
                Theme.fromValue(theme).orElseThrow(),
                FontSize.fromValue(fontSize).orElseThrow());
    }

    static void add(List<FieldError> errors, String field, Optional<FieldErrorReason> reason) {
        reason.ifPresent(value -> errors.add(new FieldError(field, value)));
    }
}
