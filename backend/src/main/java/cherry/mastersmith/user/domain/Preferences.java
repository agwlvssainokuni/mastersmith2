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

import java.util.Objects;

/**
 * 氏名と表示の設定の4つの組（契約 C4 の Preferences と同じ形。読み書きの単位）。
 *
 * <p>作れるのは決まりに合った値だけ（氏名は前後の空白を除いた後の値）。文字列化では氏名を伏せる（メソッドの呼び出しの追跡が業務処理の
 * 戻り値を文字列にするため。既存の利用者の氏名の初期値はメールアドレスで、アプリのログにメールアドレスを出さない）。
 *
 * @param displayName 氏名（前後の空白を除いた値）
 * @param language 言語
 * @param theme テーマ
 * @param fontSize 文字の大きさ
 */
public record Preferences(String displayName, Language language, Theme theme, FontSize fontSize) {

    /** 値が決まりに合うことを確かめる。 */
    public Preferences {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(fontSize, "fontSize");
        if (!Objects.equals(DisplayName.requireValid(displayName), displayName)) {
            throw new IllegalArgumentException("氏名は前後の空白を除いた値で渡してください");
        }
    }

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "Preferences[displayName=***, language=" + language.value() + ", theme=" + theme.value() + ", fontSize="
                + fontSize.value() + "]";
    }
}
