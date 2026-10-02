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
package cherry.mastersmith.useradmin.domain;

/**
 * 状態を変える5つの管理の操作の区分（Intent 260930-user-admin の U3、BR2.1・BR2.2・BR6.1）。拒否の判定と監査の種類の鍵。
 */
public enum AdminOperation {
    /** 管理者の印を付ける（POST /api/admin/users/{userId}/grant-admin）。 */
    GRANT_ADMIN,
    /** 管理者の印を外す（revoke-admin）。 */
    REVOKE_ADMIN,
    /** 利用を止める（suspend）。 */
    SUSPEND,
    /** 停止を解く（resume）。 */
    RESUME,
    /** ログインの失敗回数を戻す（reset-login-failures。ロック中ならロックも解ける）。 */
    RESET_LOGIN_FAILURES
}
