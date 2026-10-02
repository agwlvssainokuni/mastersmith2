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

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.user.service.UserAdminSummary;
import java.util.Objects;

/**
 * 利用者の一覧の1行（Intent 260930-user-admin の U3、BR1.7・BR1.8）。文字列にするとメールアドレスと氏名を伏せる（要約の文字列の形を使う）。
 *
 * @param summary 利用者の要約
 * @param lock ロックの判定の結果
 * @param self 操作している管理者自身の行か
 */
public record UserAdminEntry(UserAdminSummary summary, LockView lock, boolean self) {

    /** 値が null でないことを確かめる。 */
    public UserAdminEntry {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(lock, "lock");
    }
}
