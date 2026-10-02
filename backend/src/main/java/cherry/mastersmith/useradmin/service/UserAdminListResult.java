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
package cherry.mastersmith.useradmin.service;

import cherry.mastersmith.user.domain.FieldError;
import java.util.List;

/** 利用者の一覧の結果（Intent 260930-user-admin の U3、契約 C3 の GET。HTTP の状態は画面入出力の層が決める）。 */
public sealed interface UserAdminListResult {

    /**
     * 読んだ。
     *
     * @param page 1ページ
     */
    record Listed(UserAdminPage page) implements UserAdminListResult {}

    /** page が 1 以上の整数でない（BR1.1。項目ごとの誤りは載せない）。 */
    record InvalidPage() implements UserAdminListResult {}

    /**
     * 検索の文字の誤り（BR1.4）。
     *
     * @param errors 項目ごとの誤り（q・TOO_LONG。入れた値は持たない）
     */
    record Invalid(List<FieldError> errors) implements UserAdminListResult {

        /** 誤りの一覧を変えられない形で持つ。 */
        public Invalid {
            errors = List.copyOf(errors);
        }
    }
}
