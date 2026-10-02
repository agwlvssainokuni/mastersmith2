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
 * 管理者による氏名と言語の変更の、検証を通った組（Intent 260930-user-admin の U3、BR5.1・BR5.2）。
 *
 * <p>作れるのは決まりに合った値だけ（氏名は前後の空白を除いた後の値）。UserRepository の更新の口はこの型で受け、問い合わせの SpEL の中で
 * だけ値を取り出す。文字列にすると氏名を伏せる（{@link Preferences} と同じ形）。
 *
 * @param displayName 氏名（前後の空白を除いた値）
 * @param language 言語
 */
public record ProfileUpdate(String displayName, Language language) {

    /** 値が決まりに合うことを確かめる。 */
    public ProfileUpdate {
        Objects.requireNonNull(language, "language");
        if (!Objects.equals(DisplayName.requireValid(displayName), displayName)) {
            throw new IllegalArgumentException("氏名は前後の空白を除いた値で渡してください");
        }
    }

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "ProfileUpdate[displayName=***, language=" + language.value() + "]";
    }
}
