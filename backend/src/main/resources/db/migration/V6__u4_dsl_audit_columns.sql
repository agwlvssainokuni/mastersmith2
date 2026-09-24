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

-- U4（Intent 260923-dsl-schema-loader の DSL の管理）の監査の出来事（契約 C7）のために、監査イベントの表に列を足す。
-- 前進のみ・後方互換（NULL を許す列を足すだけで、1つ前の版のアプリも今までどおり追記できる）。
--
-- actor_user_id: 操作した管理者の利用者 ID（DSL の出来事だけ。認証・アクセス拒否の出来事では NULL）
-- dsl_hash: DSL の識別（SHA-256 の 16 進数 64 文字。読む前に大きさで断った投入では NULL）
-- dsl_source: DSL の出どころ（GENERATED・UPLOAD・PASTE・RESTORE）
-- rejection_kind: 受け付けなかった投入の理由の種類（最初の誤りの種類、または SIZE_LIMIT）
-- DSL の本文と対象DB の接続先は、どの列にも入れない（BR7.2）。
ALTER TABLE audit_events ADD COLUMN actor_user_id BIGINT;
ALTER TABLE audit_events ADD COLUMN dsl_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN dsl_source VARCHAR(16);
ALTER TABLE audit_events ADD COLUMN rejection_kind VARCHAR(32);
