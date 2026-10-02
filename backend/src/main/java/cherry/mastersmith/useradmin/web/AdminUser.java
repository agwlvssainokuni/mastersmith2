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

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.user.service.UserAdminSummary;
import cherry.mastersmith.useradmin.service.UserAdminEntry;
import java.time.Instant;

/**
 * 利用者の一覧の1行の応答（契約 C3 の AdminUser、BR1.8・BR7.5）。決めた 11 の項目だけを持ち、失敗回数・パスワードのハッシュ値・
 * トークン・ダミーの行の印を持たない。日時は ISO 8601 の UTC。文字列にするとメールアドレスと氏名を伏せる。
 *
 * @param userId 利用者 ID
 * @param email メールアドレス
 * @param displayName 氏名
 * @param language 言語（{@code ja}・{@code en}）
 * @param admin 管理者の印
 * @param suspended 利用停止中か
 * @param locked 今の時刻で判定したロック中か
 * @param lockedUntil 解除の予定の時刻（ロック中のときだけ。ほかは null）
 * @param resettable 失敗回数を戻す操作を出せるか
 * @param registeredAt 登録した日時
 * @param self 操作している管理者自身の行か
 */
public record AdminUser(
        long userId,
        String email,
        String displayName,
        String language,
        boolean admin,
        boolean suspended,
        boolean locked,
        Instant lockedUntil,
        boolean resettable,
        Instant registeredAt,
        boolean self) {

    /**
     * 業務処理の1行から作る。
     *
     * @param entry 一覧の1行
     * @return 応答の1行
     */
    static AdminUser from(UserAdminEntry entry) {
        UserAdminSummary summary = entry.summary();
        LockView lock = entry.lock();
        return new AdminUser(
                summary.userId(),
                summary.email(),
                summary.displayName(),
                summary.language().value(),
                summary.admin(),
                summary.suspended(),
                lock.locked(),
                lock.lockedUntil(),
                lock.resettable(),
                summary.registeredAt(),
                entry.self());
    }

    /** メールアドレスと氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "AdminUser[userId=" + userId + ", email=***, displayName=***, language=" + language + ", admin="
                + admin + ", suspended=" + suspended + ", locked=" + locked + ", resettable=" + resettable + ", self="
                + self + "]";
    }
}
