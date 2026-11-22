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

-- Intent 261016-business-data-views の U2（業務データの書き込み）。前進のみ・後方互換（team.md の Deployment）。
-- 適用済みの V1〜V12 は書き換えない。既存の表と行は変えず、監査イベントの表に3列だけを足す（広げてから縮める二段の「広げる」側
-- だけ）。1つ前の版のアプリは足した列を読まないため、そのまま起動できる。
--
-- audit_events に足す列（契約 C5、entities.md の BusinessDataAuditRow）。空を許し、既定値・参照の制約・索引を置かない（V6・V7・V10 と
-- 同じ形。追記だけの記録で、対象DB の行やロールが消えた後も監査の行は残る）:
--   target_schema: 業務データの操作の対象のスキーマ（写しの DSL の名前）。
--   target_table: 業務データの操作の対象のテーブル（写しの DSL の名前）。
--   work_role_id: 業務データの操作の作業ロール（作業ロールが無ければ空）。
-- 主キーの値は既存の detail の列に JSON で置く（{"key":{カラム:値},"keyTruncated":真偽値}。16,384 単位に収めるのはアプリ）。
--
-- 結果の値 UNKNOWN（確定の段の時間切れで、確定したか分からない）は、既存の result VARCHAR(16) に入り、CHECK の制約も無いため、
-- 移行は要らない。種類（event_type）・失敗の理由（failure_reason）に足す値も、既存の VARCHAR(32) に入る（どれも 30 字以内）。
ALTER TABLE audit_events ADD COLUMN target_schema VARCHAR(255);
ALTER TABLE audit_events ADD COLUMN target_table VARCHAR(255);
ALTER TABLE audit_events ADD COLUMN work_role_id BIGINT;
