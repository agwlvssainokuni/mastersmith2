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
package cherry.mastersmith.useradmin.web;

import cherry.mastersmith.user.domain.SearchText;
import org.springframework.core.convert.converter.Converter;

/**
 * 要求の q の文字列を、伏せ字の型 {@link SearchText} に包む型の変換（Intent 260930-user-admin の U3、BR1.3）。
 *
 * <p>値を包むだけで、検証・空白の除去・ログの出力をせず、例外を出さない（検証は業務処理の層で行う）。Spring の Bean にせず、
 * {@link UserAdminWebConfig} が {@code new} して登録する（メソッドの呼び出しの追跡の対象にしないため）。
 */
final class SearchTextConverter implements Converter<String, SearchText> {

    @Override
    public SearchText convert(String source) {
        return new SearchText(source);
    }
}
