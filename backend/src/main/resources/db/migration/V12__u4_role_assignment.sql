-- Copyright 2026 agwlvssainokuni
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Intent 261004-role-menu の U4（割り当てと作業ロール。Bolt B5）。前進のみ・後方互換（NFR3.6）。
-- 適用済みの V1〜V11 は書き換えない。既存の表と行は変えず、利用者への割り当ての表・グループへの割り当ての表・作業ロールの保存の表を
-- 新しく足すだけ（広げてから縮める二段の「広げる」側だけ）。users.admin_flag は残し、行を移さない（管理の可否は今までどおり
-- admin_flag で決める）。1つ前の版のアプリは足した表を読まないため、そのまま起動できる。監査イベントの表には列を足さない。
--
-- user_role_assignments の列（利用者への直接のロールの割り当て。BR6.1・BR6.2）:
--   role_id・user_id の組が主キー（重ねての割り当ては ROLE_NO_CHANGE。BR6.3・BR8.5）。assigned_at は UTC の時点。
-- group_role_assignments の列（グループへのロールの割り当て。BR6.2）:
--   role_id・group_id の組が主キー。assigned_at は UTC の時点。
-- work_role_selections の列（利用者が選んだ作業ロールの保存。1人に1行。BR7.2・BR7.7）:
--   user_id が主キー。role_id は選んだロールの ID で、ロールへの外部キーを置かない（割り当ての外のロール・消えたロールを指すことが
--   あり、そのときは読みで最初のロールに読み替える。BR7.2・BR7.4、機能設計の Q3: A）。ロールの削除では、そのロールを指す行を同じ
--   トランザクションの中で先に消す（BR3.3）。updated_at は UTC の時点。
-- 制約と索引（名前は store の区分が違反の読み替え先を決めるのに使う。変えない）:
--   pk_user_role_assignments・pk_group_role_assignments: 組の一意（同時の重なりの最後の守り。BR6.3）。
--   fk_user_role_assignments_role・fk_group_role_assignments_role: 消えたロールを指す割り当てを作らない（削除の制限。BR6.7）。
--   fk_user_role_assignments_user: 登録の終わった利用者（users の行）だけを指す（BR6.1・BR6.7）。
--   fk_group_role_assignments_group: 消えたグループを指す割り当てを作らない（削除の制限。group の BR5.4 の最後の守り）。
--   ix_user_role_assignments_user_id・ix_group_role_assignments_group_id: 利用者・グループの ID からの読み取り（有効な作業ロール・
--     数・問う口）を1回の読み取りに収める（主キーの先頭は role_id のため、user_id・group_id だけの絞り込みに効かない）。
--   pk_work_role_selections: 1人に1行（同じ利用者の初めての切り替えが重なったときの最後の守り。BR8.4）。
--   fk_work_role_selections_user: 登録の終わった利用者だけを指す。
--
-- H2 は DDL を巻き戻せないため、途中で失敗すると一部だけ当たった状態が残りうる。失敗したときは Flyway が起動を止め、配備の前の
-- バックアップから戻す（README の「戻し方」）。

CREATE TABLE user_role_assignments (
    role_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_role_assignments PRIMARY KEY (role_id, user_id)
);

ALTER TABLE user_role_assignments
    ADD CONSTRAINT fk_user_role_assignments_role FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE RESTRICT;

ALTER TABLE user_role_assignments
    ADD CONSTRAINT fk_user_role_assignments_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE RESTRICT;

CREATE INDEX ix_user_role_assignments_user_id ON user_role_assignments (user_id);

CREATE TABLE group_role_assignments (
    role_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_group_role_assignments PRIMARY KEY (role_id, group_id)
);

ALTER TABLE group_role_assignments
    ADD CONSTRAINT fk_group_role_assignments_role FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE RESTRICT;

ALTER TABLE group_role_assignments
    ADD CONSTRAINT fk_group_role_assignments_group FOREIGN KEY (group_id) REFERENCES groups (group_id)
        ON DELETE RESTRICT;

CREATE INDEX ix_group_role_assignments_group_id ON group_role_assignments (group_id);

CREATE TABLE work_role_selections (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_work_role_selections PRIMARY KEY (user_id)
);

ALTER TABLE work_role_selections
    ADD CONSTRAINT fk_work_role_selections_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE RESTRICT;
