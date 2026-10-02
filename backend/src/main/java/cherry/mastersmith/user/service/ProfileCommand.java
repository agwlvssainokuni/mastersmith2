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

/**
 * 管理者による氏名と言語の変更の入力（Intent 260930-user-admin の U3、契約 C8 の updateProfile の入力、FS の D13）。検証する前の値で、
 * どれも null でもよい。文字列にすると氏名を伏せる（{@link PreferencesCommand} と同じ形）。
 *
 * @param displayName 氏名（前後の空白を除く前の値）
 * @param language 言語
 */
public record ProfileCommand(String displayName, String language) {

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "ProfileCommand[displayName=***, language=" + language + "]";
    }
}
