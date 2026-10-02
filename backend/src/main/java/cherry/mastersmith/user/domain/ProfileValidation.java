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

/**
 * 管理者による氏名と言語の変更の入力の検証（Intent 260930-user-admin の U3、BR5.1）。DB を使わない純粋な関数。
 *
 * <p>自分のプリファレンスの保存と同じ規則（{@link DisplayName#check(String)}・{@link Language#check(String)}）で2つを検証し、誤りのある
 * 項目を要求の項目の順（displayName・language）ですべて集める。1つの項目には最初に当たった理由だけを載せ、入れた値は載せない
 * （{@link PreferencesValidation} と同じ形）。
 */
public final class ProfileValidation {

    private ProfileValidation() {}

    /**
     * 2つを検証する。
     *
     * @param displayName 氏名（前後の空白を除く前の値。null でもよい）
     * @param language 言語（null でもよい）
     * @return 項目ごとの誤り（誤りが無ければ空）
     */
    public static List<FieldError> validate(String displayName, String language) {
        List<FieldError> errors = new ArrayList<>();
        PreferencesValidation.add(errors, PreferencesValidation.DISPLAY_NAME, DisplayName.check(displayName));
        PreferencesValidation.add(errors, PreferencesValidation.LANGUAGE, Language.check(language));
        return List.copyOf(errors);
    }

    /**
     * 検証を通った2つから組を作る（氏名は前後の空白を除いた値にする）。
     *
     * @param displayName 氏名
     * @param language 言語
     * @return 組
     * @throws IllegalArgumentException 決まりに合わない値があるとき（先に {@link #validate} で確かめる。値はメッセージに載せない）
     */
    public static ProfileUpdate toProfileUpdate(String displayName, String language) {
        List<FieldError> errors = validate(displayName, language);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("氏名と言語が決まりに合いません: " + errors);
        }
        return new ProfileUpdate(
                DisplayName.strip(displayName), Language.fromValue(language).orElseThrow());
    }
}
