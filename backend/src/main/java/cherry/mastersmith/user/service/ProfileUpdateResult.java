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

import cherry.mastersmith.user.domain.FieldError;
import java.util.List;

/**
 * 管理者による氏名と言語の変更の結果（Intent 260930-user-admin の U3、契約 C8 の updateProfile）。想定内の失敗は例外にせず、この型で返す。
 */
public sealed interface ProfileUpdateResult {

    /** 書き換えた（同じ値でも成功。BR5.2）。 */
    record Updated() implements ProfileUpdateResult {}

    /** 対象の利用者がいない（BR5.2）。 */
    record NotFound() implements ProfileUpdateResult {}

    /**
     * 入力の誤り（内部DB に触れていない。BR5.1）。
     *
     * @param errors 項目ごとの誤り（1件以上、displayName・language の順。入れた値は持たない）
     */
    record Invalid(List<FieldError> errors) implements ProfileUpdateResult {

        /** 誤りの一覧を変えられない形で持つ。 */
        public Invalid {
            errors = List.copyOf(errors);
        }
    }
}
