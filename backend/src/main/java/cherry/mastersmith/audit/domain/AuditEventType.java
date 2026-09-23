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
package cherry.mastersmith.audit.domain;

/** 監査イベントの種類（BR1.2、FR9.2）。 */
public enum AuditEventType {
    /** ログインの成功。 */
    LOGIN_SUCCEEDED,
    /** ログインの失敗。 */
    LOGIN_FAILED,
    /** ログアウト。 */
    LOGGED_OUT,
    /** 管理者のみの API へのアクセスの拒否。 */
    ACCESS_DENIED
}
