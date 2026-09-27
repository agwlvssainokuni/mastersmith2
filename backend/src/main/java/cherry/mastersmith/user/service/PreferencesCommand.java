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
 * プリファレンスの保存の入力（検証する前の値。どれも null でもよい）。文字列化では氏名を伏せる（メソッドの呼び出しの追跡が業務処理の
 * 引数を文字列にするため）。
 *
 * @param displayName 氏名（前後の空白を除く前の値）
 * @param language 言語
 * @param theme テーマ
 * @param fontSize 文字の大きさ
 */
public record PreferencesCommand(String displayName, String language, String theme, String fontSize) {

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "PreferencesCommand[displayName=***, language=" + language + ", theme=" + theme + ", fontSize="
                + fontSize + "]";
    }
}
