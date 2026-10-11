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
package cherry.mastersmith.role.domain;

/**
 * ロールの操作の区分（監査の種類と、待ち合わせの口に使う。{@code entities.md} の RoleAuditEvent の type）。B4 はロールの管理と権限の
 * 設定の4つ、B5 は割り当て・外し・作業ロールの切り替えの3つ、B6 は権限の YAML の適用。
 */
public enum RoleOperation {
    /** ロールの作成。 */
    CREATE,
    /** ロールの名前の変更。 */
    RENAME,
    /** ロールの削除。 */
    DELETE,
    /** 権限の保存と、今の DSL に無い設定を消す操作。 */
    CHANGE_PERMISSIONS,
    /** 利用者・グループへのロールの割り当て（B5）。 */
    ASSIGN,
    /** 利用者・グループからのロールの割り当ての外し（B5）。 */
    UNASSIGN,
    /** 自分の作業ロールの切り替え（B5）。 */
    SWITCH_WORK_ROLE,
    /** 権限の YAML の適用（B6、BR9.10〜BR9.12・BR11.7。監査の行の対象の列は空にし、detail に書く。BR11.4）。 */
    APPLY_TRANSFER
}
