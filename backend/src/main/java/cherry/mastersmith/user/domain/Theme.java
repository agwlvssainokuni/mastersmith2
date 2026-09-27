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

import java.util.Optional;

/**
 * 利用者のテーマ（FR5.2、BR2.1）。既定は {@link #SYSTEM}（BR2.2）。{@code system} の解決は画面が行い、サーバーは値をそのまま持つ。
 */
public enum Theme implements PreferenceValue {
    /** ライト。 */
    LIGHT("light"),
    /** ダーク。 */
    DARK("dark"),
    /** OS の配色の設定に従う。 */
    SYSTEM("system");

    private final String value;

    Theme(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * 小文字の文字列の完全な一致で値を返す。
     *
     * @param raw 入力された値（null でもよい）
     * @return 値（一致しなければ空）
     */
    public static Optional<Theme> fromValue(String raw) {
        return PreferenceValueRules.find(values(), raw);
    }

    /**
     * 値が決まりに合うかを判定する。
     *
     * @param raw 入力された値（null でもよい）
     * @return 合わなければ理由（合えば空）
     */
    public static Optional<FieldErrorReason> check(String raw) {
        return PreferenceValueRules.check(values(), raw);
    }
}
