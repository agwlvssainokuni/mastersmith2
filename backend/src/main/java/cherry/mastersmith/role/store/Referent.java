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
package cherry.mastersmith.role.store;

/** 外部キーの違反の相手（制約の名前から決める。業務処理が操作ごとに読み替える。{@code reliability-design.md} 2.3、計画の D-6）。 */
public enum Referent {
    /** ロール（{@code fk_permission_settings_role}・{@code fk_user_role_assignments_role}・{@code fk_group_role_assignments_role}）。 */
    ROLE,
    /** 利用者（{@code fk_user_role_assignments_user}・{@code fk_work_role_selections_user}）。 */
    USER,
    /** グループ（{@code fk_group_role_assignments_group}）。 */
    GROUP
}
